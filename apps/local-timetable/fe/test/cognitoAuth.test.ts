import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { readCognitoConfigFromDocument } from "../src/cognitoAuth.js";

describe("cognitoAuth config", () => {
  it("returns null when cognito metas empty", () => {
    document.head.innerHTML = `
      <meta name="cognito-domain" content="" />
      <meta name="cognito-client-id" content="" />
    `;
    assert.equal(readCognitoConfigFromDocument(document), null);
  });

  it("reads domain and client id with default redirect from location", () => {
    document.head.innerHTML = `
      <meta name="cognito-domain" content="urt-lt.auth.eu-west-2.amazoncognito.com" />
      <meta name="cognito-client-id" content="abc123" />
    `;
    const cfg = readCognitoConfigFromDocument(document);
    assert.ok(cfg);
    assert.equal(cfg?.domain, "urt-lt.auth.eu-west-2.amazoncognito.com");
    assert.equal(cfg?.clientId, "abc123");
    assert.ok(cfg?.redirectUri.endsWith("/"));
  });

  it("uses explicit redirect/logout metas when set", () => {
    document.head.innerHTML = `
      <meta name="cognito-domain" content="urt-lt.auth.eu-west-2.amazoncognito.com" />
      <meta name="cognito-client-id" content="abc123" />
      <meta name="cognito-redirect-uri" content="https://d2swtj5trh3qgr.cloudfront.net" />
      <meta name="cognito-logout-uri" content="https://d2swtj5trh3qgr.cloudfront.net" />
    `;
    const cfg = readCognitoConfigFromDocument(document);
    assert.equal(cfg?.redirectUri, "https://d2swtj5trh3qgr.cloudfront.net/");
    assert.equal(cfg?.logoutUri, "https://d2swtj5trh3qgr.cloudfront.net/");
  });
});
