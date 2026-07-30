import assert from "node:assert/strict";

const calls = [];
const storage = new Map([["unispeaking.accessToken", "signed-token"]]);

globalThis.window = {
  localStorage: {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  },
};

globalThis.fetch = async (url, options = {}) => {
  calls.push({ url, options });
  return {
    ok: true,
    status: 200,
    headers: { get: () => "application/json" },
    json: async () => ({ success: true, data: {} }),
  };
};

const api = await import("../src/apiClient.js");

await api.getProfileOverview("2026-07");
await api.updateAccountProfile({ nickname: "Yufan" });
await api.uploadAvatar(new Blob(["avatar"], { type: "image/png" }));
await api.deleteAvatar();
await api.changePassword({ currentPassword: "old-secret", newPassword: "new-secret" });
await api.requestAccountDeletion({ currentPassword: "old-secret" });
await api.reactivateAccount({ username: "learner@example.com", password: "old-secret" });

assert.equal(calls[0].url, "/api/profile/overview?yearMonth=2026-07");
assert.equal(calls[0].options.headers.Authorization, "Bearer signed-token");

assert.equal(calls[1].url, "/api/account/profile");
assert.equal(calls[1].options.method, "PATCH");
assert.deepEqual(JSON.parse(calls[1].options.body), { nickname: "Yufan" });

assert.equal(calls[2].url, "/api/account/avatar");
assert.equal(calls[2].options.method, "POST");
assert.ok(calls[2].options.body instanceof FormData);
assert.equal(calls[2].options.headers["Content-Type"], undefined);
assert.ok(calls[2].options.body.get("avatar") instanceof Blob);

assert.equal(calls[3].options.method, "DELETE");
assert.equal(calls[4].url, "/api/auth/password");
assert.equal(calls[4].options.method, "PUT");
assert.deepEqual(JSON.parse(calls[4].options.body), {
  currentPassword: "old-secret",
  newPassword: "new-secret",
});

assert.equal(calls[5].url, "/api/account");
assert.equal(calls[5].options.method, "DELETE");
assert.deepEqual(JSON.parse(calls[5].options.body), { currentPassword: "old-secret" });

assert.equal(calls[6].url, "/api/auth/reactivate");
assert.equal(calls[6].options.method, "POST");
assert.deepEqual(JSON.parse(calls[6].options.body), {
  username: "learner@example.com",
  password: "old-secret",
});

console.log("Personal center API contract passed.");
