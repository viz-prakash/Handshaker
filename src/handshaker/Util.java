package handshaker;

public class Util {
	public static String exceptionToStackTraceString(Exception exception) {
		StackTraceElement[] stack = exception.getStackTrace();
		String exceptionString = "";
		for (StackTraceElement element: stack) {
			exceptionString += element.toString() + "\n\t\t";
		}
		return exceptionString;
	}

}
