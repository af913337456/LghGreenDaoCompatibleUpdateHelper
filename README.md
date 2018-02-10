 > 作者：林冠宏 / 指尖下的幽灵

> 掘金：https://juejin.im/user/587f0dfe128fe100570ce2d8

> 博客：http://www.cnblogs.com/linguanh/

> GitHub ： https://github.com/af913337456/

> 腾讯云专栏：  https://cloud.tencent.com/developer/user/1148436/activities

### GreenDaoCompatibleUpdateHelper
A helper which can help you to update you database without lost your old datas, simple to use , enjoy.

### Thanks
<a href="https://stackoverflow.com/questions/13373170/greendao-schema-update-and-data-migration/30334668#30334668">Giant</a>

### Principle & Optimization
Principle：
* A -> A + B , old: A , new: B
* use (A+B) -> create temp (A'+B') & insert data
* drop (A+B) , contain old datas
* create (A+B) , abs empty tables
* restore data to (A+B) from (A'+B') then drop (A'+B')

Optimization：
* add callback interface
* add error msg log
* fix some bug & format sql , like ``insert(name)`` => ``insert(`name`)`` which may cause some conflict。

### Usage

* change the default ``DaoMaster.DevOpenHelper`` to ``MyGreenDaoDbHelper`` in the right place.
* do your job in ``MyGreenDaoDbHelper``' s ``onUpgrade`` , like below :

```java
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
```

### Chinese Introduction

# 目录

*  出问题的的情形
*  几个事实
*  解决方案
*  代码简述
*  产品级别的可能错误
*  你的顾虑

## 出问题的的情形：
* 字段添加，导致旧表格字段与新的不匹配引发 ``android.database.sqlite.SQLiteException`` 类异常。
* 服务端数据返回无法与就表格匹配，无法进行插入操作

第一个情况会直接导致 APP 闪退掉，第二种就是数据不匹配。

## 几个事实
* GreenDao 目前的 3.+ 版，自动生成的代码的升级方式都是``先删除原来的表格，再创建新的``
```java
/** WARNING: Drops all table on Upgrade! Use only during development. */
public static class DevOpenHelper extends OpenHelper {
    ......
    @Override
    public void onUpgrade(Database db, int oldVersion, int newVersion) {
        Log.i("greenDAO", "Upgrading schema from version " + oldVersion + " to " + newVersion + " by dropping all tables");
        dropAllTables(db, true);  // 删除-----①
        onCreate(db);
    }
}
```
* 凡是自动生成的代码文件，例如 ``xxxDao.java`` 类的，都会在每一次 build 的时候重新被生成，意味着个人的内嵌修改总是无效，因为总是覆盖你的。
* 数据库的升级方式需求更多是需要往后兼容的，旧数据不能丢失

## 解决方案
自定义升级策略。
<a href="https://stackoverflow.com/questions/13373170/greendao-schema-update-and-data-migration/30334668#30334668">思路参考</a>

在上面的基础上做出如下步骤总结： (``看不懂的看下面的符号描述``)
* 创建之前旧表中不存在的新表
* 创建中间表 & 把旧表的数据迁移到中间表
* 把旧表全部删除
* 创建所有新表
* 把中间表的数据迁移到新表 & 删除中间表

对应上面的步骤描述：
* A -> A + B , old: A , new: B
* use (A+B) -> create temp (A'+B') & insert data
* drop (A+B) , contain old datas
* create (A+B) , abs empty tables
* restore data to (A+B) from (A'+B') then drop (A'+B')

## 代码简述
基于上面的二次修改和拓展

* ``GreenDaoCompatibleUpdateHelper.java``  顾名思义，兼容旧表性质的 greenDao 数据库升级，不会造成旧表的数据丢失

    * 拓展了最终的成功和失败的回调
    * 添加了错误日志的处理
    * 解决了字段名称的冲突 bug，例如 delete 之类
    
* ``MyGreenDaoDbHelper.java`` 自定义的 dbHelper，重载 onUpgrade

### 调用例子

```java
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
```

##### GreenDaoCompatibleUpdateHelper

```java
public final class GreenDaoCompatibleUpdateHelper {

    public interface GreenDaoCompatibleUpdateCallBack{
        void onFinalSuccess();
        void onFailedLog(String errorMsg);
    }

    private static GreenDaoCompatibleUpdateCallBack callBack;

    @SuppressWarnings("all")
    public void compatibleUpdate(SQLiteDatabase sqliteDatabase, Class<? extends AbstractDao<?, ?>>... daoClasses) {
        StandardDatabase db = new StandardDatabase(sqliteDatabase);
        /** 创建之前旧表中不存在的新表 */
        if(!generateNewTablesIfNotExists_withNoExchangeData(db, daoClasses))    
            return;
        /** 创建中间表 & 把旧表的数据迁移到中间表 */
        if(!generateTempTables_withExchangeDataFromOldTable(db, daoClasses))   
            return;
        /** 把旧表全部删除 */
        if(!dropAllTables(db, true, daoClasses))                         
            return;
        /** 创建所有新表 */
        if(!createAllTables_withNoExchangeData(db, false, daoClasses)) 
            return;
        /** 把中间表的数据迁移到新表 & 删除中间表 */
        restoreData_fromTempTableToNewTable(db, daoClasses);                     
        if(callBack != null)
            callBack.onFinalSuccess();
        callBack = null;
    }

    public GreenDaoCompatibleUpdateHelper setCallBack(GreenDaoCompatibleUpdateCallBack callBack1){
        callBack = callBack1;
        return this;
    }
    ...... // 去 gitHub 下载完整代码
}
```

##### MyGreenDaoDbHelper

```java
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
```

## 产品级别的可能错误

* 因为混淆了 dao 类文件，导致 createTable 方法找不到，解决方法，不要混淆 dao 文件
* restore 步骤中因为新加入的字段含有 int boolean 基础类型，因为不具备默认值而导致出现 ``SQLiteConstraintException: NOT NULL constraint failed`` 错误，解决方法，采用 Integer Boolean 类型替换，这个你只能妥协，因为 greenDao 作者不屑于在你建表的时候提供默认值方法。详情可以去看看 issue

## 你的顾虑

* 如果我的表太多了，升级会不会造成 ANR 或者导致读写混乱？
* 是否实践过？

1, 答： sqlLite 的源码里面调用 ``onUpdrade``方法的入口皆加上了``同步琐``，这样不会造成在升级中还能让你去读写的情况。
这点设计得非常优秀！表太多的，几百张？那么就放入子线程升级。

2, 答： 我已经使用到线上多个APP ， 且成功运行至今。

