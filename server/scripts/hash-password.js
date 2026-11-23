// 사용법: node scripts/hash-password.js <비밀번호>
// 출력된 해시를 users.json의 passwordHash에 넣습니다.
const bcrypt = require('bcrypt');

const password = process.argv[2];
if (!password) {
  console.error('사용법: node scripts/hash-password.js <비밀번호>');
  process.exit(1);
}

bcrypt.hash(password, 10).then((hash) => console.log(hash));
