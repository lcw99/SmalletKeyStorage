package co.smallet.keystorage.contentprovider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashMap;

public class KeyVaultContentProvider extends ContentProvider {
    private static final String AUTHORITY = "co.smallet.keystorage.provider.KeyVaultContentProvider";
    private static final String PUBLIC_KEY_TABLE = "publickeys";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PUBLIC_KEY_TABLE);

    static final public String _ID = "_id";
    static final public String PUBLICKEY = "publickey";
    static final public String KEYINDEX = "keyindex";
    static final public String OWNER = "owner";
    static final public String HDCOINID = "hdcoinid";

    private static HashMap<String, String> PUBLICKEYS_PROJECTION_MAP = new HashMap<>();

    static final int PUBLICKEYS = 1;
    static final int PUBLICKEY_ID = 2;

    static final UriMatcher uriMatcher;
    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(AUTHORITY, "publickeys", PUBLICKEYS);
        uriMatcher.addURI(AUTHORITY, "publickeys/#", PUBLICKEY_ID);
    }

    /**
     * Database specific constant declarations
     */

    private static SQLiteDatabase db;
    static final String DATABASE_NAME = "KeyVault";
    static final String PUBLICKEYS_TABLE_NAME = "publickeys";
    static final int DATABASE_VERSION = 1;
    static final String CREATE_DB_TABLE =
            " CREATE TABLE " + PUBLICKEYS_TABLE_NAME +
                    " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    " owner TEXT NOT NULL, " +
                    " publickey TEXT NOT NULL, " +
                    " keyindex INTEGER NOT NULL, " +
                    " hdcoinid INTEGER NOT NULL);";

    /**
     * Helper class that actually creates and manages
     * the provider's underlying data repository.
     */

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(CREATE_DB_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + PUBLICKEYS_TABLE_NAME);
            onCreate(db);
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        PUBLICKEYS_PROJECTION_MAP.put(_ID, _ID);
        PUBLICKEYS_PROJECTION_MAP.put(PUBLICKEY, PUBLICKEY);
        PUBLICKEYS_PROJECTION_MAP.put(KEYINDEX, KEYINDEX);
        PUBLICKEYS_PROJECTION_MAP.put(OWNER, OWNER);
        PUBLICKEYS_PROJECTION_MAP.put(HDCOINID, HDCOINID);

        /**
         * Create a write able database which will trigger its
         * creation if it doesn't already exist.
         */

        db = dbHelper.getWritableDatabase();
        return db != null;
    }

    public static void myInsert(ContentValues values) {
        long rowID = db.insert(PUBLICKEYS_TABLE_NAME, "", values);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (true)
            throw new SQLException("Not arrowed to access " + uri);

        /**
         * Add a new student record
         */
        long rowID = db.insert(PUBLICKEYS_TABLE_NAME, "", values);

        /**
         * If record is added successfully
         */
        if (rowID > 0) {
            Uri _uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
            getContext().getContentResolver().notifyChange(_uri, null);
            return _uri;
        }

        throw new SQLException("Failed to add a record into " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
                        String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        qb.setTables(PUBLICKEYS_TABLE_NAME);

        switch (uriMatcher.match(uri)) {
            case PUBLICKEYS:
                qb.setProjectionMap(PUBLICKEYS_PROJECTION_MAP);
                break;

            case PUBLICKEY_ID:
                qb.appendWhere( _ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
        }

        if (sortOrder == null || sortOrder == ""){
            /**
             * By default sort on student names
             */
            sortOrder = KEYINDEX;
        }


        Cursor c = qb.query(db,	projection,	selection,
                selectionArgs,null, null, sortOrder);
        /**
         * register to watch a content URI for changes
         */
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    public static Cursor queryPublicAddress(String owner, Integer hdCoinId, Integer keyIndex) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        qb.setTables(PUBLICKEYS_TABLE_NAME);

        String[] projection = {
                PUBLICKEY,
                KEYINDEX,
                OWNER,
                HDCOINID
        };

        String selection = null;
        ArrayList<String> selectionArgs = null;
        if (owner != null) {
            selection = OWNER + " = ? ";
            selectionArgs = new ArrayList<>();
            selectionArgs.add(owner);
            if (hdCoinId != -1) {
                selection += " AND " + HDCOINID + " = ? ";
                selectionArgs.add(hdCoinId.toString());
            }
            if (keyIndex != -1) {
                selection += " AND " + KEYINDEX + " = ? ";
                selectionArgs.add(keyIndex.toString());
            }
        }
        String[] selArg = null;
        if (selectionArgs != null)
            selArg = selectionArgs.toArray(new String[0]);
        return qb.query(db, projection,	selection, selArg, null, null, KEYINDEX);
    }

    public static void myDelete(String selection, String[] selectionArgs) {
        db.delete(PUBLICKEYS_TABLE_NAME, selection, selectionArgs);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (true)
            throw new SQLException("Not arrowed to access " + uri);

        int count = 0;
        switch (uriMatcher.match(uri)){
            case PUBLICKEYS:
                count = db.delete(PUBLICKEYS_TABLE_NAME, selection, selectionArgs);
                break;

            case PUBLICKEY_ID:
                String id = uri.getPathSegments().get(1);
                count = db.delete(PUBLICKEYS_TABLE_NAME, _ID +  " = " + id +
                                (!TextUtils.isEmpty(selection) ? "AND (" + selection + ')' : ""), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values,
                      String selection, String[] selectionArgs) {
        if (true)
            throw new SQLException("Not arrowed to access " + uri);

        int count = 0;
        switch (uriMatcher.match(uri)) {
            case PUBLICKEYS:
                count = db.update(PUBLICKEYS_TABLE_NAME, values, selection, selectionArgs);
                break;

            case PUBLICKEY_ID:
                count = db.update(PUBLICKEYS_TABLE_NAME, values,
                        _ID + " = " + uri.getPathSegments().get(1) +
                                (!TextUtils.isEmpty(selection) ? "AND (" +selection + ')' : ""), selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri );
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)){
            /**
             * Get all student records
             */
            case PUBLICKEYS:
                return "vnd.android.cursor.dir/vnd.example.students";
            /**
             * Get a particular student
             */
            case PUBLICKEY_ID:
                return "vnd.android.cursor.item/vnd.example.students";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }
}
