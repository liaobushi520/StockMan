package com.liaobusi.stockman

import com.liaobusi.stockman.db.AppDatabase

/** 删除 ZTReplayBean 表中 expound、expound2、reason2 同时为 null 或空串的行；返回删除行数。 */
fun AppDatabase.deleteZTReplayRowsWhereExpoundFieldsAllEmpty(): Int =
    ztReplayDao().deleteWhereExpoundExpound2Reason2AllEmpty()
