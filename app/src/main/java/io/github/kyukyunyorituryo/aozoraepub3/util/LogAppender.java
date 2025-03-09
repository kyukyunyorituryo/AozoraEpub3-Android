package io.github.kyukyunyorituryo.aozoraepub3.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

/** ログ出力Wrapperクラス */
public class LogAppender {
	private static TextView textView = null;
	private static final Handler handler = new Handler(Looper.getMainLooper());

	public static void setTextView(TextView _textView) {
		textView = _textView;
	}

	public static void println(String log) {
		append(log);
		append("\n");
	}

	public static void println() {
		append("\n");
	}

	public static void append(String log) {
		Log.d("LogAppender", log);  // Androidのログ出力
		if (textView != null) {
			handler.post(() -> {
				textView.append(log);
				textView.append("\n");
			});
		}
	}

	public static void printStackTrace(Exception e) {
		for (StackTraceElement ste : e.getStackTrace()) {
			append(ste.toString());
			append("\n");
		}
	}

	public static void msg(int lineNum, String msg, String desc) {
		append(msg + " (" + (lineNum + 1) + ")");
		if (desc != null) {
			append(" : " + desc);
		}
		append("\n");
	}

	public static void error(String msg) {
		append("[ERROR] " + msg + "\n");
	}

	public static void error(int lineNum, String msg, String desc) {
		append("[ERROR] ");
		msg(lineNum, msg, desc);
	}

	public static void error(int lineNum, String msg) {
		append("[ERROR] ");
		msg(lineNum, msg, null);
	}

	public static void warn(int lineNum, String msg, String desc) {
		append("[WARN] ");
		msg(lineNum, msg, desc);
	}

	public static void warn(int lineNum, String msg) {
		append("[WARN] ");
		msg(lineNum, msg, null);
	}

	public static void info(int lineNum, String msg, String desc) {
		append("[INFO] ");
		msg(lineNum, msg, desc);
	}

	public static void info(int lineNum, String msg) {
		append("[INFO] ");
		msg(lineNum, msg, null);
	}
}
