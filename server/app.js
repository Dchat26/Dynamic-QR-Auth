const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const bodyParser = require('body-parser');
const bcrypt = require('bcrypt');
const cors = require('cors');
const QRCode = require('qrcode');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });
const PORT = 2454;

let items = [];
const clients = {};
const clientQrRequests = {};
const errorCounts = {};

// 계정 정보는 코드에 두지 않고 users.json(.gitignore 대상)에서 읽어옵니다.
// 형식은 users.example.json 참고, 비밀번호 해시는 scripts/hash-password.js로 생성합니다.
const fs = require('fs');
const path = require('path');
const USERS_FILE = path.join(__dirname, 'users.json');
const users = fs.existsSync(USERS_FILE)
  ? JSON.parse(fs.readFileSync(USERS_FILE, 'utf8'))
  : [];
if (users.length === 0) {
  console.warn('users.json이 없어 로그인 가능한 계정이 없습니다. README의 실행 방법을 참고하세요.');
}

app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));
app.use(cors());

function logErrorOnce(code, error) {
  if (!errorCounts[code]) {
    errorCounts[code] = true; 
    console.error(`Error for code "${code}":`, error);
    handleError(code); 
  }
}

function handleError(code) {
  delete clientQrRequests[code];
  delete clients[code];
  items = items.filter(item => item.code !== code); 
}

app.post('/login', (req, res) => {
  const { username, password } = req.body;
  console.log(`Received login request for username: ${username}`);

  const user = users.find(user => user.username === username);
  if (!user) {
    return res.status(404).json({ error: '사용자를 찾을 수 없습니다.' });
  }

  bcrypt.compare(password, user.passwordHash, (err, result) => {
    if (err) {
      return res.status(500).json({ error: '비밀번호 비교 중 오류가 발생했습니다.' });
    }

    if (result) {
      return res.status(200).json({ message: '로그인 성공' });
    } else {
      return res.status(401).json({ error: '비밀번호가 올바르지 않습니다.' });
    }
  });
});

app.post('/items', (req, res) => {
  const item = req.body;
  items.push(item);
  console.log(`Item added: code = "${item.code}" , status = "${item.status}"`);

  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(JSON.stringify({ type: 'ADD', item }));
    }
  });

  res.status(201).json(item);
});

app.get('/items', (req, res) => {
  res.json(items);
});

app.delete('/items/:id', (req, res) => {
  const id = req.params.id;
  const item = items.find(item => item.id === id);

  if (item) {
    items = items.filter(item => item.id !== id);
    console.log(`Item deleted: code="${item.code}"`);

    if (clientQrRequests[item.code]) {
      clearInterval(clientQrRequests[item.code].intervalId);
      delete clientQrRequests[item.code];
    }

    if (clients[item.code]) {
      clients[item.code].close();
      delete clients[item.code];
    }

    wss.clients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(JSON.stringify({ type: 'DELETE', id }));
      }
    });

    res.status(204).send();
  } else {
    res.status(404).json({ error: '아이템을 찾을 수 없습니다.' });
  }
});

app.post('/generate_qr', (req, res) => {
  const { code } = req.body;
  if (!code) {
    return res.status(400).json({ error: 'code가 필요합니다.' });
  }

  if (clients[code]) {
    if (clientQrRequests[code]) {
      clearInterval(clientQrRequests[code].intervalId);
    }

    const item = items.find(item => item.code === code);
    if (item) {
      item.status = 'off';
    }

    const authCode = Math.floor(100000 + Math.random() * 900000).toString();
    clientQrRequests[code] = { authCode };

    sendQrCodeToClient(clients[code], code);
    clientQrRequests[code].intervalId = startQrSending(code);

    res.status(200).json({ message: 'QR 코드 생성 요청을 받았습니다.' });
  } else {
    res.status(404).json({ error: '해당 기기에 연결된 클라이언트를 찾을 수 없습니다.' });
  }
});


function sendQrCodeToClient(client, code) {
  try {
    if (client.readyState !== WebSocket.OPEN) {
      handleError(code);
      return;
    }

    const qrContent = code;
    const authCode = Math.floor(100000 + Math.random() * 900000).toString();
    const randomToken = Math.random().toString(36).substring(2, 8).toUpperCase(); 

    if (!clientQrRequests[code]) {
      clientQrRequests[code] = { authCode, randomToken, qrSendCount: 0 };
    }

    clientQrRequests[code].authCode = authCode;
    clientQrRequests[code].randomToken = randomToken;
    clientQrRequests[code].qrSendCount++;

    QRCode.toDataURL(`${qrContent}-${randomToken}`, (err, url) => {
      if (err) {
        throw err;
      }

      if (client.readyState === WebSocket.OPEN) {
        client.send(JSON.stringify({ type: 'QR_CODE', qrContent, authCode, randomToken }));
        console.log(`QR 코드 전송됨: ${qrContent}, 인증번호: ${authCode}, 토큰 값: ${randomToken}, 전송 횟수: ${clientQrRequests[code].qrSendCount}`);
      }
    });
  } catch (error) {
    logErrorOnce(code, error);
  }
}


function startQrSending(code) {
  clientQrRequests[code].qrSendCount = 0; 

  const intervalId = setInterval(() => {
    if (!clientQrRequests[code] || clientQrRequests[code].qrSendCount >= 9) {
      clearInterval(intervalId);
      delete clientQrRequests[code];
      console.log(`${code}의 QR 코드 전송이 중단되었습니다.`);

      const item = items.find(item => item.code === code);
      if (item && item.status === 'off') {
        item.status = 'on';
      }
      return;
    }

    sendQrCodeToClient(clients[code], code); 
  }, 30000);

  clientQrRequests[code].intervalId = intervalId;
  return intervalId;
}

app.post('/verify_auth_code', (req, res) => {
  const { code, authCode, randomToken } = req.body;

  if (!code || !authCode || !randomToken) {
      return res.status(400).json({ success: false, message: '인증 코드와 임의 값이 필요합니다.' });
  }

  const request = clientQrRequests[code];

  if (request && request.authCode === authCode && request.randomToken === randomToken) {
      const item = items.find(item => item.code === code);
      if (item) {
          item.status = 'off';
      }

      if (clientQrRequests[code].intervalId) {
          clearInterval(clientQrRequests[code].intervalId);
      }
      delete clientQrRequests[code];

      console.log(`인증 성공: "${code}" `);

      wss.clients.forEach(client => {
          if (client.readyState === WebSocket.OPEN) {
              client.send(JSON.stringify({ type: 'AUTH_SUCCESS', code }));
          }
      });

      return res.status(200).json({ success: true, message: '인증 성공' });
  } else {
      return res.status(401).json({ success: false, message: '인증 실패' });
  }
});


app.post('/stop_qr', (req, res) => {
  const { code } = req.body;

  if (clientQrRequests[code]) {
    clearInterval(clientQrRequests[code].intervalId);
    delete clientQrRequests[code];

    const item = items.find(item => item.code === code);
    if (item && item.status === 'off') {
      item.status = 'on';
    }

    res.status(200).json({ message: 'QR 코드 전송이 중단되었습니다.' });
  } else {
    res.status(404).json({ message: '해당 코드의 QR 전송 세션을 찾을 수 없습니다.' });
  }
});

app.post('/return', (req, res) => {
  const { code } = req.body;
  console.log(`${code} returned`);

  if (!code || code.trim() === '') {
    return res.status(400).json({ message: '유효한 code가 필요합니다.' });
  }

  const item = items.find(item => item.code === code);
  if (item) {
    item.status = 'on';

    wss.clients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(JSON.stringify({ type: 'RETURN', code }));
      }
    });

    return res.status(200).json({ message: '반납 성공' });
  } else {
    return res.status(404).json({ message: '해당 코드를 가진 아이템이 없습니다.' });
  }
});

wss.on('connection', ws => {
  ws.on('message', message => {
    const data = JSON.parse(message);

    if (data.type === 'REGISTER_DEVICE') {
      const { deviceCode } = data;

      const itemExists = items.some(item => item.code === deviceCode);

      if (itemExists) {
        clients[deviceCode] = ws;
        console.log(`Device registered: ${deviceCode}`);
        
        ws.send(JSON.stringify({ type: 'REGISTER_SUCCESS', deviceCode }));
      } else {
        ws.send(JSON.stringify({ type: 'REGISTER_FAILED', message: '등록 실패: 유효하지 않은 deviceCode 입니다.' }));
      }
    }
  });

  ws.on('close', () => {
    for (let code in clients) {
      if (clients[code] === ws) {
        delete clients[code];
        break;
      }
    }
  });
});

process.on('uncaughtException', (error) => {
  console.error("Uncaught Exception 발생:", error);
});

process.on('unhandledRejection', (reason, promise) => {
  console.error("Unhandled Rejection 발생:", reason);
});

server.listen(PORT, () => {
  console.log(`${PORT} running`);
});
