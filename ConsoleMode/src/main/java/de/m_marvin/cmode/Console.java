package de.m_marvin.cmode;

public class Console {
	
	static {
		NativeLoader.setTempLibFolder(System.getProperty("java.io.tmpdir") + "/jcmode");
		NativeLoader.setLibLoadConfig("/libload_consolemode.cfg");
		NativeLoader.loadNative("jcmode");
	}
	
	public static enum Mode {
		DEFAULT(0),
		ANSI(1),
		ANSI_EVENTS(2);
		
		private final int code;
		
		private Mode(int code) {
			this.code = code;
		}
		
		public int getCode() {
			return code;
		}
	}

	public static boolean ansiEventMode() {
		return setConsoleMode(Mode.ANSI_EVENTS);
	}
	
	public static boolean ansiMode() {
		return setConsoleMode(Mode.ANSI);
	}
	
	// This should never fail, since it resets to what it was set previously
	public static void defaultMode() {
		setConsoleMode(Mode.DEFAULT);
	}
	
	// Note: wrapping has no effect with Mode.DEFAULT
	public static boolean setConsoleMode(Mode mode) {
		return setMode_n(mode.getCode());
	}
	
	protected static native boolean setMode_n(int mode);
	
}
