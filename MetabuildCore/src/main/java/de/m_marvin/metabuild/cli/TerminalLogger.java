package de.m_marvin.metabuild.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import de.m_marvin.simplelogging.LogLevel;
import de.m_marvin.simplelogging.impl.SystemLogger;

public class TerminalLogger extends SystemLogger {
	
	protected ByteArrayOutputStream uiBuffer;
	
	public TerminalLogger(boolean colored) {
		super(colored);
	}
	
	public TerminalLogger() {
		super();
	}
	
	public PrintStream getNewPrinter() {
		this.uiBuffer = new ByteArrayOutputStream();
		return new PrintStream(this.uiBuffer);
	}
	
	public void printUI(boolean end) {
		if (this.uiBuffer == null) return;
		String ui = this.uiBuffer.toString(StandardCharsets.UTF_8);
		System.out.print(ui);
		
		if (!end)System.out.print(String.format("\033[%dA\r", ui.split("\n").length));
		// LOAD CURSOR
	}
	
	@Override
	public void print(LogLevel level, String msg) {
		super.print(level, msg);
		// SAVE CURSOR
		printUI(false);
	}
	
}
