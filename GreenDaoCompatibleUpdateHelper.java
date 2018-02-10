package DBCache.cache;

/**
 * Created by lgh on 18-2-9.
 */

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.StandardDatabase;
import org.greenrobot.greendao.internal.DaoConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Createdby PedroOkawa and modified by MBH on 16/08/16.
 * Rebuild by Lgh on 2018-02-09
 */

/**
 * 兼容旧表性质的 greenDao 数据库升级，不会造成旧表的数据丢失
 */

public final class GreenDaoCompatibleUpdateHelper {

    public interface GreenDaoCompatibleUpdateCallBack{
        void onFinalSuccess();
        void onFailedLog(String errorMsg);
    }

    private static GreenDaoCompatibleUpdateCallBack callBack;

    @SuppressWarnings("all")
    public void compatibleUpdate(SQLiteDatabase sqliteDatabase, Class<? extends AbstractDao<?, ?>>... daoClasses) {
        StandardDatabase db = new StandardDatabase(sqliteDatabase);
        if(!generateNewTablesIfNotExists_withNoExchangeData(db, daoClasses))    /** 创建之前旧表中不存在的新表 */
            return;
        if(!generateTempTables_withExchangeDataFromOldTable(db, daoClasses))    /** 创建中间表 & 把旧表的数据迁移到中间表 */
            return;
        if(!dropAllTables(db, true, daoClasses))                         /** 把旧表全部删除 */
            return;
        if(!createAllTables_withNoExchangeData(db, false, daoClasses)) /** 创建所有新表 */
            return;
        restoreData_fromTempTableToNewTable(db, daoClasses);                     /** 把中间表的数据迁移到新表 & 删除中间表 */
        if(callBack != null)
            callBack.onFinalSuccess();
        callBack = null;
    }

    @SuppressWarnings("all")
    public void compatibleUpdate(StandardDatabase db, Class<? extends AbstractDao<?, ?>>... daoClasses) {
        if(! generateNewTablesIfNotExists_withNoExchangeData(db, daoClasses))
            return;
        if(! generateTempTables_withExchangeDataFromOldTable(db, daoClasses))
            return;
        if(! dropAllTables(db, true, daoClasses))
            return;
        if(! createAllTables_withNoExchangeData(db, false, daoClasses))
            return;
        restoreData_fromTempTableToNewTable(db, daoClasses);
        if(callBack != null)
            callBack.onFinalSuccess();
        callBack = null;
    }

    public GreenDaoCompatibleUpdateHelper setCallBack(GreenDaoCompatibleUpdateCallBack callBack1){
        callBack = callBack1;
        return this;
    }

    @SafeVarargs
    private static boolean generateNewTablesIfNotExists_withNoExchangeData(StandardDatabase db, Class<? extends AbstractDao<?, ?>>... daoClasses) {
        return reflectMethod(db, "createTable", true, daoClasses);
    }

    @SafeVarargs
    private static boolean generateTempTables_withExchangeDataFromOldTable(StandardDatabase db, Class<? extends AbstractDao<?, ?>>... daoClasses) {
        try {
            for (int i = 0; i < daoClasses.length; i++) {
                DaoConfig daoConfig = new DaoConfig(db, daoClasses[i]);
                String tableName = daoConfig.tablename;
                String tempTableName = daoConfig.tablename.concat("_TEMP");
                StringBuilder insertTableStringBuilder = new StringBuilder();
                insertTableStringBuilder.append("CREATE TEMP TABLE ").append(tempTableName);
                insertTableStringBuilder.append(" AS SELECT * FROM ").append(tableName).append(";");
                db.execSQL(insertTableStringBuilder.toString());
            }
            return true;
        }catch (Exception e){
            if(callBack != null)
                callBack.onFailedLog("generateTempTables_withExchangeDataFromOldTable ===> "+e.toString());
        }
        return false;
    }

    @SafeVarargs
    private static boolean dropAllTables(StandardDatabase db, boolean ifExists, @NonNull Class<? extends AbstractDao<?, ?>>... daoClasses) {
        return reflectMethod(db, "dropTable", ifExists, daoClasses);
    }

    @SafeVarargs
    private static boolean createAllTables_withNoExchangeData(StandardDatabase db, boolean ifNotExists, @NonNull Class<? extends AbstractDao<?, ?>>... daoClasses) {
        return reflectMethod(db, "createTable", ifNotExists, daoClasses);
    }

    /**
     * dao class already define the sql exec method, so just invoke it
     */
    @SafeVarargs
    private static boolean reflectMethod(StandardDatabase db, String methodName, boolean isExists, @NonNull Class<? extends AbstractDao<?, ?>>... daoClasses) {
        if (daoClasses.length < 1) {
            if(callBack != null)
                callBack.onFailedLog("reflectMethod ===> daoClasses.length < 1");
            return false;
        }
        try {
            for (Class cls : daoClasses) {
                Method method = cls.getDeclaredMethod(methodName, Database.class, boolean.class);
                method.invoke(null, db, isExists);
            }
            // restoreData_fromTempTableToNewTable
            // ===>
            // android.database.sqlite.SQLiteConstraintException: NOT NULL constraint failed: MATTER_USER_BEAN.STATUS (code 1299)
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            if(callBack != null)
                callBack.onFailedLog("reflectMethod ===> "+e.toString());
        }
        return false;
    }

    /**
     * 把旧表的数据复制到新表，不存在的字段默认值
     * @param db
     * @param daoClasses
     */
    @SafeVarargs
    private static void restoreData_fromTempTableToNewTable(StandardDatabase db, Class<? extends AbstractDao<?, ?>>... daoClasses) {
        try {
            for (int i = 0; i < daoClasses.length; i++) {
                DaoConfig daoConfig = new DaoConfig(db, daoClasses[i]);
                String tableName = daoConfig.tablename;
                String tempTableName = daoConfig.tablename.concat("_TEMP");
                // get all columns from tempTable, take careful to use the columns list
                List<String> columns = getColumns(db, tempTableName);
                ArrayList<String> properties = new ArrayList<>(columns.size());
                for (int j = 0; j < daoConfig.properties.length; j++) {
                    String columnName = daoConfig.properties[j].columnName;
                    if (columns.contains(columnName)) {
                        properties.add(columnName);
                    }
                }
                if (properties.size() > 0) {
                    final String columnSQL = "`"+TextUtils.join("`,`", properties)+"`";
                    StringBuilder insertTableStringBuilder = new StringBuilder();
                    insertTableStringBuilder.append("INSERT INTO ").append(tableName).append(" (");
                    insertTableStringBuilder.append(columnSQL);
                    insertTableStringBuilder.append(") SELECT ");
                    insertTableStringBuilder.append(columnSQL);
                    insertTableStringBuilder.append(" FROM ").append(tempTableName).append(";");
                    db.execSQL(insertTableStringBuilder.toString());
                }
                StringBuilder dropTableStringBuilder = new StringBuilder();
                dropTableStringBuilder.append("DROP TABLE ").append(tempTableName);
                db.execSQL(dropTableStringBuilder.toString());
            }
        }catch (Exception e){
            if(callBack != null)
                callBack.onFailedLog("restoreData_fromTempTableToNewTable ===> "+e.toString());
        }
    }

    private static List<String> getColumns(StandardDatabase db, String tableName) {
        List<String> columns = null;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT * FROM " + tableName + " limit 0", null);
            if (null != cursor && cursor.getColumnCount() > 0) {
                columns = Arrays.asList(cursor.getColumnNames());
            }
        } catch (Exception e) {
            if(callBack != null)
                callBack.onFailedLog("getColumns ===> "+e.toString());
        } finally {
            if (cursor != null)
                cursor.close();
            if (null == columns)
                columns = new ArrayList<>();
        }
        return columns;
    }

}