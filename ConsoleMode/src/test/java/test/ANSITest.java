package test;

import java.io.IOException;
import java.io.InputStream;

import de.m_marvin.cmode.Console;
import de.m_marvin.cmode.NonBlockingInputStream;
import de.m_marvin.cmode.Console.Mode;

public class ANSITest {
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		if (!Console.setConsoleMode(Mode.ANSI)) {
			System.err.println("No ANSI/VT100 support!");
			System.exit(-1);
		}
		
		InputStream inb = new NonBlockingInputStream(System.in);
		
		System.out.print("\033[6n");
		Thread.sleep(1);
		String in = new String(inb.readAllBytes());
		in = in.replace('\033', 'E');
		System.out.println("read: " + in);

		Console.setConsoleMode(Mode.DEFAULT);

		inb.close();
		
	}
	
}
