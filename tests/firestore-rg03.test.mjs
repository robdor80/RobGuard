import fs from 'node:fs';
import { after, test } from 'node:test';
import { strict as assert } from 'node:assert';
import {
  initializeTestEnvironment, assertFails, assertSucceeds
} from '@firebase/rules-unit-testing';
import {
  Timestamp, doc, collection, getDocFromServer, getDocsFromServer,
  setDoc, updateDoc, deleteDoc, serverTimestamp
} from 'firebase/firestore';

const OWNER = 'wNVy8SirO8RZi8PvffWER0wRqrj2';
const PROJECT = 'demo-robguard-rg03';
const env = await initializeTestEnvironment({
  projectId: PROJECT,
  firestore: { rules: fs.readFileSync('firestore.rg03.rules', 'utf8') }
});
after(async () => { await env.cleanup(); });

const google = (uid = OWNER) => env.authenticatedContext(uid, {
  firebase: { sign_in_provider: 'google.com' }
}).firestore();
const anon = uid => env.authenticatedContext(uid, {
  firebase: { sign_in_provider: 'anonymous' }
}).firestore();
const unauth = () => env.unauthenticatedContext().firestore();
const vault = db => doc(db, 'vaults', OWNER);
const device = (db, uid) => doc(db, 'devices', uid);
const encrypted = (revision = 1) => ({
  schema: 2, revision, entries: 'encrypted-base64-only',
  wrappedDek: 'encrypted-wrapped-dek-only'
});
const base = (uid, status, extra = {}) => ({
  schema: 3, deviceUid: uid, ownerUid: OWNER,
  installationId: 'fixed-local-installation-identity',
  alias: 'Equipo de pruebas',
  publicKey: 'mock-public-key-x509-only',
  status, wipeRequested: false, createdAt: Timestamp.now(), ...extra
});
async function setup(statuses = { first: 'authorized' }) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(vault(db), encrypted());
    for (const [uid, status] of Object.entries(statuses)) {
      await setDoc(device(db, uid), base(uid, status));
    }
  });
}

test('RG-02 Google UID alone cannot read or update the old vault', async () => {
  await setup();
  await assertFails(getDocFromServer(vault(google())));
  await assertFails(updateDoc(vault(google()), { revision: 2 }));
});
test('unauthenticated, unknown and pending installations cannot read vault', async () => {
  await setup({ first: 'authorized', pending: 'pending' });
  await assertFails(getDocFromServer(vault(unauth())));
  await assertFails(getDocFromServer(vault(anon('rogue'))));
  await assertFails(getDocFromServer(vault(anon('pending'))));
});
test('authorized installation can read, and update using revision +1', async () => {
  await setup();
  await assertSucceeds(getDocFromServer(vault(anon('first'))));
  await assertSucceeds(updateDoc(vault(anon('first')), {
    entries: 'different-authenticated-cipher', revision: 2
  }));
});
test('revision jumps, deletion and recovery-key replacement are rejected', async () => {
  await setup();
  const db = anon('first');
  await assertFails(updateDoc(vault(db), { revision: 4 }));
  await assertFails(updateDoc(vault(db), { wrappedDek: 'replaced', revision: 2 }));
  await assertFails(deleteDoc(vault(db)));
});
test('owner Google may propose an installation but cannot approve it', async () => {
  await setup();
  const db = google();
  await assertSucceeds(setDoc(device(db, 'candidate'), {
    ...base('candidate', 'pending'), createdAt: serverTimestamp()
  }));
  await assertFails(updateDoc(device(db, 'candidate'), {
    status: 'authorized', approvedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(device(db, 'candidate'), { wipeRequested: true }));
});
test('unauthorized Google and anonymous users cannot propose an installation', async () => {
  await setup();
  await assertFails(setDoc(device(google('not-the-owner'), 'deviceZ'), {
    ...base('deviceZ', 'pending'), createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(device(anon('rogue'), 'rogue'), {
    ...base('rogue', 'pending'), createdAt: serverTimestamp()
  }));
});
test('approved device can approve a second; cannot approve itself', async () => {
  await setup({ first: 'authorized', second: 'pending' });
  const first = anon('first');
  await assertSucceeds(updateDoc(device(first, 'second'), {
    status: 'authorized', approvedAt: serverTimestamp()
  }));
  await assertSucceeds(getDocFromServer(vault(anon('second'))));
  await assertFails(updateDoc(device(first, 'first'), {
    status: 'authorized', approvedAt: serverTimestamp()
  }));
});
test('revocation stops vault reads/writes even with an old anonymous token', async () => {
  await setup({ first: 'authorized', second: 'authorized' });
  await assertSucceeds(getDocFromServer(vault(anon('second'))));
  await assertSucceeds(updateDoc(device(anon('first'), 'second'), {
    status: 'revoked', wipeRequested: true, revokedAt: serverTimestamp()
  }));
  await assertFails(getDocFromServer(vault(anon('second'))));
  await assertFails(updateDoc(vault(anon('second')), { revision: 2 }));
  await assertFails(updateDoc(device(anon('second'), 'second'), {
    status: 'authorized'
  }));
  await assertSucceeds(getDocFromServer(device(anon('second'), 'second')));
});
test('revoked devices cannot approve other pending installations', async () => {
  await setup({ first: 'revoked', pending: 'pending' });
  await assertFails(updateDoc(device(anon('first'), 'pending'), {
    status: 'authorized', approvedAt: serverTimestamp()
  }));
});
test('self-revocation is blocked; revocation requires another active device', async () => {
  await setup({ first: 'authorized', second: 'authorized' });
  await assertFails(updateDoc(device(anon('first'), 'first'), {
    status: 'revoked', wipeRequested: true, revokedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(device(anon('outsider'), 'first'), {
    status: 'revoked', wipeRequested: true, revokedAt: serverTimestamp()
  }));
});
test('wipe acknowledgement only from revoked target and only with wipe order', async () => {
  await setup({
    first: 'authorized', second: 'revoked', third: 'revoked'
  });
  await env.withSecurityRulesDisabled(async ctx => {
    await updateDoc(device(ctx.firestore(), 'second'), { wipeRequested: true });
  });
  await assertFails(updateDoc(device(anon('first'), 'second'), {
    status: 'wipe_confirmed', wipedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(device(anon('third'), 'third'), {
    status: 'wipe_confirmed', wipedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(device(anon('second'), 'second'), {
    status: 'wipe_confirmed', wipedAt: serverTimestamp()
  }));
  await assertFails(getDocFromServer(vault(anon('second'))));
});
test('device inventory is visible only to active device', async () => {
  await setup({ first: 'authorized', second: 'pending', third: 'revoked' });
  const docs = await assertSucceeds(getDocsFromServer(collection(anon('first'), 'devices')));
  assert.equal(docs.size, 3);
  await assertFails(getDocsFromServer(collection(anon('second'), 'devices')));
  await assertFails(getDocsFromServer(collection(google(), 'devices')));
});
test('a forged ownerUid on a device never grants vault access', async () => {
  await setup({ first: 'authorized' });
  await env.withSecurityRulesDisabled(async ctx => {
    await updateDoc(device(ctx.firestore(), 'first'), { ownerUid: 'different-owner' });
  });
  await assertFails(getDocFromServer(vault(anon('first'))));
});
test('disallowed schema fields cannot change through device update', async () => {
  await setup({ first: 'authorized', second: 'pending' });
  await assertFails(updateDoc(device(anon('first'), 'second'), {
    status: 'authorized', approvedAt: serverTimestamp(), ownerUid: 'attacker'
  }));
  await assertFails(updateDoc(device(anon('first'), 'second'), {
    status: 'authorized', approvedAt: serverTimestamp(), publicKey: 'fake-new'
  }));
});
