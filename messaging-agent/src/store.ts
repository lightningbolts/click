import Database from "better-sqlite3";
import fs from "node:fs";
import path from "node:path";

export type PhoneProfile = {
  phone_e164: string;
  zip: string | null;
  display_name: string | null;
  latitude: number | null;
  longitude: number | null;
  updated_at: string;
};

export type ChatTurn = {
  role: "user" | "assistant";
  content: string;
  created_at: string;
};

export class AgentStore {
  private readonly db: Database.Database;

  constructor(sqlitePath: string) {
    const dir = path.dirname(path.resolve(sqlitePath));
    fs.mkdirSync(dir, { recursive: true });
    this.db = new Database(sqlitePath);
    this.db.pragma("journal_mode = WAL");
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS phone_profiles (
        phone_e164 TEXT PRIMARY KEY,
        zip TEXT,
        display_name TEXT,
        latitude REAL,
        longitude REAL,
        updated_at TEXT NOT NULL
      );

      CREATE TABLE IF NOT EXISTS chat_turns (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        phone_e164 TEXT NOT NULL,
        role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
        content TEXT NOT NULL,
        created_at TEXT NOT NULL
      );

      CREATE INDEX IF NOT EXISTS idx_chat_turns_phone
        ON chat_turns(phone_e164, id DESC);
    `);
  }

  getProfile(phoneE164: string): PhoneProfile {
    const row = this.db
      .prepare(
        `SELECT phone_e164, zip, display_name, latitude, longitude, updated_at
         FROM phone_profiles WHERE phone_e164 = ?`,
      )
      .get(phoneE164) as PhoneProfile | undefined;
    if (row) return row;
    const now = new Date().toISOString();
    this.db
      .prepare(
        `INSERT INTO phone_profiles (phone_e164, zip, display_name, latitude, longitude, updated_at)
         VALUES (?, NULL, NULL, NULL, NULL, ?)`,
      )
      .run(phoneE164, now);
    return {
      phone_e164: phoneE164,
      zip: null,
      display_name: null,
      latitude: null,
      longitude: null,
      updated_at: now,
    };
  }

  setZip(
    phoneE164: string,
    zip: string,
    latitude: number,
    longitude: number,
  ): PhoneProfile {
    const now = new Date().toISOString();
    this.getProfile(phoneE164);
    this.db
      .prepare(
        `UPDATE phone_profiles
         SET zip = ?, latitude = ?, longitude = ?, updated_at = ?
         WHERE phone_e164 = ?`,
      )
      .run(zip, latitude, longitude, now, phoneE164);
    return this.getProfile(phoneE164);
  }

  setDisplayName(phoneE164: string, displayName: string): PhoneProfile {
    const now = new Date().toISOString();
    this.getProfile(phoneE164);
    this.db
      .prepare(
        `UPDATE phone_profiles
         SET display_name = ?, updated_at = ?
         WHERE phone_e164 = ?`,
      )
      .run(displayName, now, phoneE164);
    return this.getProfile(phoneE164);
  }

  appendTurn(phoneE164: string, role: "user" | "assistant", content: string): void {
    this.db
      .prepare(
        `INSERT INTO chat_turns (phone_e164, role, content, created_at)
         VALUES (?, ?, ?, ?)`,
      )
      .run(phoneE164, role, content, new Date().toISOString());
  }

  recentTurns(phoneE164: string, limit = 12): ChatTurn[] {
    const rows = this.db
      .prepare(
        `SELECT role, content, created_at FROM chat_turns
         WHERE phone_e164 = ?
         ORDER BY id DESC
         LIMIT ?`,
      )
      .all(phoneE164, limit) as ChatTurn[];
    return rows.reverse();
  }

  close(): void {
    this.db.close();
  }
}
