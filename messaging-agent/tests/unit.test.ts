import { createHmac } from "node:crypto";
import { describe, expect, it } from "vitest";
import { filterWithinRadiusKm, haversineKm } from "../src/haversine.js";
import { normalizeUsZip } from "../src/geocode.js";
import { normalizeE164 } from "../src/tools.js";
import { truncateSms } from "../src/sendSms.js";
import { validateTwilioSignature } from "../src/twilioWebhook.js";

describe("haversineKm", () => {
  it("is ~0 for the same point", () => {
    expect(haversineKm(37.77, -122.42, 37.77, -122.42)).toBeLessThan(0.001);
  });

  it("measures SF to Oakland roughly 13–20 km", () => {
    const km = haversineKm(37.7749, -122.4194, 37.8044, -122.2712);
    expect(km).toBeGreaterThan(10);
    expect(km).toBeLessThan(25);
  });
});

describe("filterWithinRadiusKm", () => {
  it("keeps only events inside the radius and attaches distance", () => {
    const items = [
      { id: "near", latitude: 37.78, longitude: -122.41 },
      { id: "far", latitude: 34.05, longitude: -118.25 },
      { id: "noloc", latitude: null, longitude: null },
    ];
    const out = filterWithinRadiusKm(items, 37.77, -122.42, 40);
    expect(out.map((x) => x.id)).toEqual(["near"]);
    expect(out[0].distance_km).toBeLessThan(5);
  });
});

describe("normalizeUsZip", () => {
  it("parses bare and embedded ZIPs", () => {
    expect(normalizeUsZip("94107")).toBe("94107");
    expect(normalizeUsZip("I'm in 94107")).toBe("94107");
    expect(normalizeUsZip("94107-1234")).toBe("94107");
    expect(normalizeUsZip("nope")).toBeNull();
  });
});

describe("normalizeE164", () => {
  it("normalizes US numbers", () => {
    expect(normalizeE164("+1 (555) 123-4567")).toBe("+15551234567");
    expect(normalizeE164("5551234567")).toBe("+15551234567");
    expect(normalizeE164("12")).toBeNull();
  });
});

describe("truncateSms", () => {
  it("truncates long bodies", () => {
    const long = "a".repeat(2000);
    const out = truncateSms(long, 100);
    expect(out.length).toBe(100);
    expect(out.endsWith("…")).toBe(true);
  });
});

describe("validateTwilioSignature", () => {
  it("accepts a correct HMAC signature", () => {
    const authToken = "test_token";
    const url = "https://example.com/webhooks/twilio";
    const params = { From: "+15551234567", Body: "hi" };
    const data =
      url +
      Object.keys(params)
        .sort()
        .map((k) => k + params[k as keyof typeof params])
        .join("");
    const signature = createHmac("sha1", authToken)
      .update(Buffer.from(data, "utf-8"))
      .digest("base64");
    expect(validateTwilioSignature(authToken, signature, url, params)).toBe(
      true,
    );
    expect(validateTwilioSignature(authToken, "nope", url, params)).toBe(false);
  });
});
