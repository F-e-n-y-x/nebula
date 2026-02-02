package com.limelight.computers;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.limelight.LimeLog;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * SQLiteOpenHelper implementation for managing computer database with proper version management.
 * This class consolidates all legacy database migrations into a single upgrade path.
 * 
 * Database Version History:
 * - Version 1 (computers.db): Original schema with byte blob addresses
 * - Version 2 (computers2.db): Added serverCert, separate address fields
 * - Version 3 (computers3.db): Combined addresses with delimiters, added IPv6
 * - Version 4 (computers4.db): JSON format for addresses (current)
 */
public class ComputerDatabaseHelper extends SQLiteOpenHelper {
    
    private static final String DATABASE_NAME = "computers.db";
    private static final int DATABASE_VERSION = 4;
    
    private static final String TABLE_NAME = "Computers";
    private static final String COL_UUID = "UUID";
    private static final String COL_NAME = "ComputerName";
    private static final String COL_ADDRESSES = "Addresses";
    private static final String COL_MAC = "MacAddress";
    private static final String COL_CERT = "ServerCert";
    
    // Legacy database file names
    private static final String[] LEGACY_DB_FILES = {
        "computers2.db",  // V2
        "computers3.db",  // V3
        "computers4.db"   // V4 (previous current)
    };
    
    // Legacy V1 constants
    private static final String V1_ADDRESS_PREFIX = "ADDRESS_PREFIX__";
    
    // Legacy V3 constants
    private static final char V3_ADDR_DELIM = ';';
    private static final char V3_PORT_DELIM = '_';
    
    // JSON address field names (V4+)
    interface AddressFields {
        String LOCAL = "local";
        String REMOTE = "remote";
        String MANUAL = "manual";
        String IPv6 = "ipv6";
        String ADDRESS = "address";
        String PORT = "port";
    }
    
    private final Context context;
    private List<ComputerDetails> pendingMigrations;
    private boolean migrationCollected = false;
    
    public ComputerDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(String.format((Locale) null,
                "CREATE TABLE IF NOT EXISTS %s(%s TEXT PRIMARY KEY, %s TEXT NOT NULL, %s TEXT NOT NULL, %s TEXT, %s BLOB)",
                TABLE_NAME, COL_UUID, COL_NAME, COL_ADDRESSES, COL_MAC, COL_CERT));
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LimeLog.info("Upgrading database from version " + oldVersion + " to " + newVersion);
        // Future schema upgrades go here:
        // if (oldVersion < 5) { db.execSQL("ALTER TABLE ..."); }
    }
    
    /**
     * Lazily collect and return pending migrations from legacy databases.
     * This is called once and then cleared.
     */
    public synchronized List<ComputerDetails> getPendingMigrations() {
        if (!migrationCollected) {
            pendingMigrations = collectAllLegacyData();
            migrationCollected = true;
        }
        
        List<ComputerDetails> result = pendingMigrations;
        pendingMigrations = null;
        return result != null ? result : new LinkedList<>();
    }
    
    /**
     * Check if there are any legacy databases to migrate.
     * This is a fast check that doesn't read any data.
     */
    public boolean hasLegacyDatabases() {
        // Check V1 database (same name but different schema)
        java.io.File v1File = context.getDatabasePath(DATABASE_NAME);
        if (v1File.exists() && isLegacyV1Schema(v1File)) {
            return true;
        }
        
        // Check other legacy database files
        for (String legacyDb : LEGACY_DB_FILES) {
            if (context.getDatabasePath(legacyDb).exists()) {
                return true;
            }
        }
        return false;
    }
    
    // ======================== Migration Collection ========================
    
    private List<ComputerDetails> collectAllLegacyData() {
        List<ComputerDetails> allComputers = new LinkedList<>();
        
        // V1: computers.db with old schema (must check before we overwrite it)
        allComputers.addAll(migrateLegacyDb(DATABASE_NAME, this::isLegacyV1Schema, this::parseV1Cursor, true));
        
        // V2-V4: separate database files
        allComputers.addAll(migrateLegacyDb("computers2.db", null, this::parseV2Cursor, true));
        allComputers.addAll(migrateLegacyDb("computers3.db", null, this::parseV3Cursor, true));
        allComputers.addAll(migrateLegacyDb("computers4.db", null, this::parseV4Cursor, true));
        
        if (!allComputers.isEmpty()) {
            LimeLog.info("Collected " + allComputers.size() + " computers from legacy databases");
        }
        
        return allComputers;
    }
    
    /**
     * Generic method to migrate a legacy database.
     * 
     * @param dbName Database file name
     * @param schemaChecker Optional check to verify this is the expected schema (for V1)
     * @param parser Cursor parser for this database version
     * @param deleteAfter Whether to delete the database after migration
     */
    private List<ComputerDetails> migrateLegacyDb(
            String dbName,
            SchemaChecker schemaChecker,
            CursorParser parser,
            boolean deleteAfter) {
        
        java.io.File dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            return new LinkedList<>();
        }
        
        List<ComputerDetails> computers = new LinkedList<>();
        
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
            
            // Optional schema check (for V1 which shares the same filename)
            if (schemaChecker != null && !schemaChecker.isExpectedSchema(dbFile)) {
                return computers;
            }
            
            try (Cursor c = db.rawQuery("SELECT * FROM " + TABLE_NAME, null)) {
                while (c.moveToNext()) {
                    ComputerDetails details = parser.parse(c);
                    if (details != null && details.uuid != null) {
                        computers.add(details);
                    }
                }
            }
        } catch (Exception e) {
            LimeLog.warning("Failed to migrate " + dbName + ": " + e.getMessage());
        }
        
        if (deleteAfter && !computers.isEmpty()) {
            context.deleteDatabase(dbName);
            LimeLog.info("Deleted legacy database: " + dbName);
        }
        
        return computers;
    }
    
    // ======================== Schema Checker ========================
    
    @FunctionalInterface
    private interface SchemaChecker {
        boolean isExpectedSchema(java.io.File dbFile);
    }
    
    private boolean isLegacyV1Schema(java.io.File dbFile) {
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = db.rawQuery("PRAGMA table_info(" + TABLE_NAME + ")", null)) {
            
            boolean hasOldNameColumn = false;
            boolean hasNewAddressesColumn = false;
            
            while (cursor.moveToNext()) {
                String colName = cursor.getString(1);
                if ("name".equalsIgnoreCase(colName)) hasOldNameColumn = true;
                if ("Addresses".equalsIgnoreCase(colName)) hasNewAddressesColumn = true;
            }
            
            // V1 has 'name' column but not 'Addresses' column
            return hasOldNameColumn && !hasNewAddressesColumn;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ======================== Cursor Parsers ========================
    
    @FunctionalInterface
    private interface CursorParser {
        ComputerDetails parse(Cursor c);
    }
    
    private ComputerDetails parseV1Cursor(Cursor c) {
        try {
            ComputerDetails details = new ComputerDetails();
            details.name = c.getString(0);
            details.uuid = c.getString(1);
            details.localAddress = parseV1Address(c, 2, details.name, "local");
            details.remoteAddress = parseV1Address(c, 3, details.name, "remote");
            details.manualAddress = details.remoteAddress;
            details.macAddress = c.getString(4);
            details.state = ComputerDetails.State.UNKNOWN;
            return details;
        } catch (Exception e) {
            LimeLog.severe("Failed to parse V1 computer: " + e.getMessage());
            return null;
        }
    }
    
    private ComputerDetails.AddressTuple parseV1Address(Cursor c, int col, String name, String type) {
        try {
            // Try as byte blob first (very old format)
            byte[] blob = c.getBlob(col);
            if (blob != null) {
                String addr = InetAddress.getByAddress(blob).getHostAddress();
                LimeLog.warning("DB: Legacy " + type + " address (blob) for " + name);
                return new ComputerDetails.AddressTuple(addr, NvHTTP.DEFAULT_HTTP_PORT);
            }
        } catch (UnknownHostException | IllegalStateException ignored) {}
        
        // Try as prefixed string
        String str = c.getString(col);
        if (str != null && str.startsWith(V1_ADDRESS_PREFIX)) {
            return new ComputerDetails.AddressTuple(
                    str.substring(V1_ADDRESS_PREFIX.length()), 
                    NvHTTP.DEFAULT_HTTP_PORT);
        }
        return null;
    }
    
    private ComputerDetails parseV2Cursor(Cursor c) {
        try {
            ComputerDetails details = new ComputerDetails();
            details.uuid = c.getString(0);
            details.name = c.getString(1);
            details.localAddress = tupleFromString(c.getString(2));
            details.remoteAddress = tupleFromString(c.getString(3));
            details.manualAddress = tupleFromString(c.getString(4));
            details.macAddress = c.getString(5);
            details.serverCert = parseCertificate(c, 6);
            details.state = ComputerDetails.State.UNKNOWN;
            return details;
        } catch (Exception e) {
            LimeLog.severe("Failed to parse V2 computer: " + e.getMessage());
            return null;
        }
    }
    
    private ComputerDetails parseV3Cursor(Cursor c) {
        try {
            ComputerDetails details = new ComputerDetails();
            details.uuid = c.getString(0);
            details.name = c.getString(1);
            
            String[] addrs = c.getString(2).split(String.valueOf(V3_ADDR_DELIM), -1);
            details.localAddress = parseV3Address(addrs, 0);
            details.remoteAddress = parseV3Address(addrs, 1);
            details.manualAddress = parseV3Address(addrs, 2);
            details.ipv6Address = parseV3Address(addrs, 3);
            
            details.externalPort = details.remoteAddress != null 
                    ? details.remoteAddress.port : NvHTTP.DEFAULT_HTTP_PORT;
            details.macAddress = c.getString(3);
            details.serverCert = parseCertificate(c, 4);
            details.state = ComputerDetails.State.UNKNOWN;
            return details;
        } catch (Exception e) {
            LimeLog.severe("Failed to parse V3 computer: " + e.getMessage());
            return null;
        }
    }
    
    private ComputerDetails.AddressTuple parseV3Address(String[] addrs, int index) {
        if (index >= addrs.length || addrs[index].isEmpty()) return null;
        String[] parts = addrs[index].split(String.valueOf(V3_PORT_DELIM), -1);
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : NvHTTP.DEFAULT_HTTP_PORT;
        return new ComputerDetails.AddressTuple(parts[0], port);
    }
    
    private ComputerDetails parseV4Cursor(Cursor c) {
        try {
            ComputerDetails details = new ComputerDetails();
            details.uuid = c.getString(0);
            details.name = c.getString(1);
            
            JSONObject addrs = new JSONObject(c.getString(2));
            details.localAddress = tupleFromJson(addrs, AddressFields.LOCAL);
            details.remoteAddress = tupleFromJson(addrs, AddressFields.REMOTE);
            details.manualAddress = tupleFromJson(addrs, AddressFields.MANUAL);
            details.ipv6Address = tupleFromJson(addrs, AddressFields.IPv6);
            
            details.externalPort = details.remoteAddress != null 
                    ? details.remoteAddress.port : NvHTTP.DEFAULT_HTTP_PORT;
            details.macAddress = c.getString(3);
            details.serverCert = parseCertificate(c, 4);
            details.state = ComputerDetails.State.UNKNOWN;
            return details;
        } catch (Exception e) {
            LimeLog.severe("Failed to parse V4 computer: " + e.getMessage());
            return null;
        }
    }
    
    // ======================== Common Utilities ========================
    
    private ComputerDetails.AddressTuple tupleFromString(String addr) {
        return (addr == null || addr.isEmpty()) ? null 
                : new ComputerDetails.AddressTuple(addr, NvHTTP.DEFAULT_HTTP_PORT);
    }
    
    public static ComputerDetails.AddressTuple tupleFromJson(JSONObject json, String name) throws JSONException {
        if (!json.has(name) || json.isNull(name)) return null;
        JSONObject addr = json.getJSONObject(name);
        return new ComputerDetails.AddressTuple(
                addr.getString(AddressFields.ADDRESS), 
                addr.getInt(AddressFields.PORT));
    }
    
    private X509Certificate parseCertificate(Cursor c, int col) {
        if (c.getColumnCount() <= col) return null;
        try {
            byte[] certData = c.getBlob(col);
            if (certData != null) {
                return (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(certData));
            }
        } catch (Exception e) {
            LimeLog.warning("Failed to parse certificate: " + e.getMessage());
        }
        return null;
    }
}
