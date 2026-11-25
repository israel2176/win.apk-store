package app.apkstore.data

import android.content.Context
import androidx.annotation.StringRes
import java.io.IOException

/**
 * שגיאה עם טקסט מתורגם: שכבת הנתונים לא יודעת באיזו שפה הממשק,
 * ולכן היא שומרת רק את מזהה המחרוזת, והממשק מתרגם (values / values-iw).
 */
open class AppException(@StringRes val res: Int, vararg val args: Any) : IOException("string#$res ${args.joinToString()}") {
    fun text(ctx: Context): String = ctx.getString(res, *args)
}

/** טקסט של כל שגיאה, בשפת המערכת */
fun Throwable.localized(ctx: Context): String = when (this) {
    is AppException -> text(ctx)
    is NetFreeBlockException -> ctx.getString(app.apkstore.R.string.err_blocked)
    is java.net.UnknownHostException -> ctx.getString(app.apkstore.R.string.err_offline)
    else -> message ?: ctx.getString(app.apkstore.R.string.err_generic)
}
