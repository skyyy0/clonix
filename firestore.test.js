const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_sky_123";
const BOB_UID = "bob_other_456";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validClonePayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    packageName: "com.whatsapp",
    originalAppName: "WhatsApp",
    cloneLabel: "WhatsApp • US iPhone",
    instanceNumber: 1,
    devicePlatform: "IOS",
    regionCode: "US",
    regionName: "United States",
    city: "New York, NY",
    timezoneId: "America/New_York",
    localeTag: "en-US",
    carrierName: "T-Mobile US",
    mccMnc: "310-260",
    virtualModel: "iPhone 16 Pro Max",
    virtualManufacturer: "Apple",
    virtualAndroidId: "8f3c11a2b4d90e12",
    virtualFingerprintHash: "A1B2C3D4E5F60718",
    launchMode: "ISOLATED_CONTAINER",
    targetWebUrl: "https://web.whatsapp.com",
    proxyEnabled: true,
    proxySummary: "HTTP 45.12.33.10:8080",
    createdAt: now,
    updatedAt: now,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("Unauthenticated user cannot read or list clones", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("clones").get());
});

test("Owner can create, read, and update their iOS/Android clone record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cloneRef = aliceDb.collection("users").doc(ALICE_UID).collection("clones").doc("clone_1");
  await assertSucceeds(cloneRef.set(validClonePayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("clones").get());
  await assertSucceeds(cloneRef.update({ cloneLabel: "WhatsApp • JP iPhone", updatedAt: new Date() }));
});

test("Cross-user access to clones and auditLogs is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cloneRef = aliceDb.collection("users").doc(ALICE_UID).collection("clones").doc("clone_1");
  await assertSucceeds(cloneRef.set(validClonePayload(ALICE_UID)));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("clones").doc("clone_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("clones").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("clones").doc("clone_2").set(validClonePayload(BOB_UID)));
});

test("Shadow update with ghost field or invalid platform enum is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badClone = validClonePayload(ALICE_UID);
  badClone.isAdminBypass = true;
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("clones").doc("clone_ghost").set(badClone));

  const badPlatform = validClonePayload(ALICE_UID);
  badPlatform.devicePlatform = "WINDOWS_FAKE";
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("clones").doc("clone_plat").set(badPlatform));
});

test("Audit logs allow create by owner and reject update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("auditLogs").doc("log_1");
  await assertSucceeds(logRef.set({
    userId: ALICE_UID,
    cloneId: "clone_1",
    eventType: "PROVISION",
    title: "WhatsApp • US iPhone",
    detail: "iOS 18.3 • New York",
    createdAt: new Date(),
  }));
  await assertFails(logRef.update({ title: "Tampered" }));
});
