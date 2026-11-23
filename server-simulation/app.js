const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const bodyParser = require('body-parser');
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
const pendingQrRequests = {};
let simulationInterval = null; 

// 시뮬레이션(시연) 전용 데모 계정입니다. 실제 인증 서버(server/)는 bcrypt 해시와 users.json을 사용합니다.
const users = [
  {
    username: 'demo1',
    password: 'test'
  },
  {
    username: 'demo2',
    password: 'test'
  },
  {
    username: 'demo3',
    password: 'test'
  }
];

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

  if (user.password === password) {
    return res.status(200).json({ message: '로그인 성공' });
  } else {
    return res.status(401).json({ error: '비밀번호가 올바르지 않습니다.' });
  }
});

app.post('/items', (req, res) => {
  const item = req.body;
  items.push(item);
  console.log(`Item added: code = "${item.code}" , status = "${item.status}", battery = "${item.battery}"`);

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
    return res.status(404).json({ error: '아이템을 찾을 수 없습니다.' });
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
    } else {
      clientQrRequests[code].qrSendCount++; 
    }

    clientQrRequests[code].authCode = authCode;
    clientQrRequests[code].randomToken = randomToken;

    QRCode.toDataURL(`${qrContent}-${randomToken}`, (err, url) => {
      if (err) {
        throw err;
      }

      if (client.readyState === WebSocket.OPEN) {
        client.send(JSON.stringify({ type: 'QR_CODE', qrContent, authCode, randomToken }));
        console.log(`QR 코드 전송됨: ${qrContent}, 인증번호: ${authCode}, 토큰 값: ${randomToken}`);
      }
    });
  } catch (error) {
    logErrorOnce(code, error);
  }
}

function startSimulation() {
  if (!simulationInterval) {
    simulationInterval = setInterval(simulateMultipleAuth, 10000);
    console.log("Simulation started.");
  }
}

function stopSimulation() {
  if (simulationInterval) {
    clearInterval(simulationInterval);
    simulationInterval = null;
    console.log("Simulation stopped.");
  }
}

function simulateMultipleAuth() {
  const availableItems = items.filter(item => !pendingQrRequests[item.code] && item.battery > 25); 
  if (availableItems.length === 0) return;

  const randomIndex = Math.floor(Math.random() * availableItems.length);
  const randomItem = availableItems[randomIndex];
  const code = randomItem.code;

  if (!clientQrRequests[code]) {
    clientQrRequests[code] = { qrSendCount: 0 };
  }

  if (clientQrRequests[code].qrSendCount < 10) {
    clientQrRequests[code].authCode = Math.floor(100000 + Math.random() * 900000).toString();
    clientQrRequests[code].qrSendCount += 1; 

    const client = clients[code];
    if (client && client.readyState === WebSocket.OPEN) {
      sendQrCodeToClient(client, code); 
      pendingQrRequests[code] = true;

      const item = items.find(item => item.code === code);
      if (item) item.status = 'off';

      clientQrRequests[code].intervalId = setInterval(() => {
        if (clientQrRequests[code].qrSendCount < 10 && client.readyState === WebSocket.OPEN) {
          sendQrCodeToClient(client, code); 
        } else {
          clearInterval(clientQrRequests[code].intervalId);
        }
      }, 30000); 
    }

    const randomDelay = Math.floor(Math.random() * (60000 - 5000 + 1)) + 5000;
    setTimeout(() => {
      console.log(`Simulating AUTH_SUCCESS for item code: ${code}`);
      
      wss.clients.forEach(client => {
        if (client.readyState === WebSocket.OPEN && clientQrRequests[code]) {
          client.send(JSON.stringify({ type: 'AUTH_SUCCESS', code }));
        }
      });

      const item = items.find(item => item.code === code);
      if (item) item.status = 'on';

      delete pendingQrRequests[code];
      clearInterval(clientQrRequests[code].intervalId);
      delete clientQrRequests[code];
    }, randomDelay);
  } else {
    console.log(`${code} has reached maximum QR send count.`);
  }
}



wss.on('connection', ws => {
  ws.on('message', message => {
    const data = JSON.parse(message);

    if (data.type === 'REGISTER_DEVICE') {
      const { deviceCode } = data;

      const itemExists = items.some(item => item.code === deviceCode);

      if (itemExists) {
        clients[deviceCode] = ws;
        
        ws.send(JSON.stringify({ type: 'REGISTER_SUCCESS', deviceCode })); 
        checkAndControlSimulation();
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
    checkAndControlSimulation();
  });
});

function checkAndControlSimulation() {
  if (Object.keys(clients).length > 2) {
    startSimulation();
  } else {
    stopSimulation();
  }
}

process.on('uncaughtException', (error) => {
  console.error("Uncaught Exception 발생:", error);
});

process.on('unhandledRejection', (reason, promise) => {
  console.error("Unhandled Rejection 발생:", reason);
});

server.listen(PORT, () => {
  console.log(`${PORT} running`);
});
