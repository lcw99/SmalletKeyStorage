package co.smallet.keystorage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;

public class KeystorageDatabase {
    private static SQLiteDatabase db;
    static final String DATABASE_NAME = "Wallet";
    static final int DATABASE_VERSION = 2;

    static public String PRIVATE_KEY_TABLE_NAME = "privatekeys";

    static public String PUBLIC_KEY = "public_key";
    static public String PRIVATE_KEY_DATA = "private_key_data";
    static public String PRIVATE_KEY_IV = "private_key_iv";
    static final String CREATE_PRIVATE_KEY_DB_TABLE =
            " CREATE TABLE " + PRIVATE_KEY_TABLE_NAME +
                    " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    PUBLIC_KEY + " TEXT NOT NULL, " +
                    PRIVATE_KEY_DATA + " TEXT NOT NULL, " +
                    PRIVATE_KEY_IV +" TEXT NOT NULL );";

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_PRIVATE_KEY_DB_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + CREATE_PRIVATE_KEY_DB_TABLE);
            onCreate(db);
        }
    }

    public KeystorageDatabase(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    public long insert(String tableName, ContentValues values) {
        long rowID = db.insert(tableName, "", values);
        return rowID;
    }

    public long insertPrivateKey(String publicKey, String privateKeyData, String privateKeyIv) {
        ContentValues values = new ContentValues();
        values.put(PUBLIC_KEY, publicKey);
        values.put(PRIVATE_KEY_DATA, privateKeyData);
        values.put(PRIVATE_KEY_IV, privateKeyIv);
        return insert(PRIVATE_KEY_TABLE_NAME, values);
    }

    public void delete(String tableName, String selection, String[] selectionArgs) {
        db.delete(tableName, selection, selectionArgs);
    }

    public Cursor query(String tableName, String[] projection,
                        String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(tableName);

        Cursor c = qb.query(db,	projection,	selection,
                selectionArgs,null, null, sortOrder);
        return c;
    }

    public Cursor queryPrivateKeyEncrypted(String publicKey) {
        String[] projection = {
                PUBLIC_KEY,
                PRIVATE_KEY_DATA,
                PRIVATE_KEY_IV
        };
        String selection = PUBLIC_KEY + " = ?";
        String[] selectionArgs = { publicKey };
        return query(PRIVATE_KEY_TABLE_NAME, projection, selection, selectionArgs, null);
    }

    public int update(String tableName, ContentValues values,
                      String selection, String[] selectionArgs) {
        int count = db.update(tableName, values, selection, selectionArgs);
        return count;
    }

}
