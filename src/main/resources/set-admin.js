const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json'); // el mismo que usa tu backend

admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });

admin.auth().getUserByEmail('zharicknicoleha@ufps.edu.co')
  .then(user => admin.auth().setCustomUserClaims(user.uid, { rol: 'ADMINISTRADOR' }))
  .then(() => { console.log('Claim seteado'); process.exit(0); });