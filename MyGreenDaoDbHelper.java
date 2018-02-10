package DBCache.cache.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.greenrobot.greendao.database.Database;

import DBCache.ChannelChatsBeanDao;
import DBCache.DaoMaster;
import DBCache.JoinToChannelReqBeanDao;
import DBCache.MatterUserBeanDao;
import DBCache.PostBeanDao;
import DBCache.PropsBeanDao;
import DBCache.cache.GreenDaoCompatibleUpdateHelper;

/**
 * Created by lgh on 18-2-9.
 */

public class MyGreenDaoDbHelper extends DaoMaster.DevOpenHelper {

    public MyGreenDaoDbHelper(Context context, String name) {
        super(context, name);
    }

    public MyGreenDaoDbHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
    }

    @Override
    @SuppressWarnings("all")
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        super.onUpgrade(db, oldVersion, newVersion);
        Log.e("MyGreenDaoDbHelper", "----"+oldVersion + "---先前和更新之后的版本---" + newVersion+"----");
        if (oldVersion < newVersion) {
            Log.e("MyGreenDaoDbHelper","进行数据库升级");
            new GreenDaoCompatibleUpdateHelper()
                    .setCallBack(
                            new GreenDaoCompatibleUpdateHelper.GreenDaoCompatibleUpdateCallBack() {
                                @Override
                                public void onFinalSuccess() {
                                    Log.e("MyGreenDaoDbHelper","进行数据库升级 ===> 成功");
                                }

                                @Override
                                public void onFailedLog(String errorMsg) {
                                    Log.e("MyGreenDaoDbHelper","升级失败日志 ===> "+errorMsg);
                                }
                            }
                    )
                    .compatibleUpdate(
                            db,
                            PostBeanDao.class,
                            MatterUserBeanDao.class,
                            PropsBeanDao.class,
                            ChannelChatsBeanDao.class,
                            JoinToChannelReqBeanDao.class
                    );
            Log.e("MyGreenDaoDbHelper","进行数据库升级--完成");
        }
    }

    @Override
    public void onUpgrade(Database db, int oldVersion, int newVersion) {
        // 不要调用父类的，它默认是先删除全部表再创建
        // super.onUpgrade(db, oldVersion, newVersion);

    }
}
