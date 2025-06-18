package de.m_marvin.metabuild.core.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.simplelogging.api.Logger;

public class ProcessUtility {

	private ProcessUtility() {}
	
	public static int runProcess(Logger logger, ProcessBuilder processBuilder) throws BuildException {
		return runProcess(logger, processBuilder, () -> false);
	}
	
	public static int runProcess(Logger logger, ProcessBuilder processBuilder, Supplier<Boolean> abortSwitch) throws BuildException {
		
		try {
			
			// Start process
			Process process = processBuilder.start();
			
			// Pipe output to logger
			Thread stoutPipe = new Thread(() -> {
				InputStreamReader source = new InputStreamReader(process.getInputStream());
				PrintWriter target = logger.infoPrinterRaw();
				try {
					source.transferTo(target);
				} catch (IOException e) {} finally {
					target.flush();
				}
			}, "PipeStdOut");
			Thread sterrPipe = new Thread(() -> {
				InputStreamReader source = new InputStreamReader(process.getErrorStream());
				PrintWriter target = logger.errorPrinterRaw();
				try {
					source.transferTo(target);
				} catch (IOException e) {} finally {
					target.flush();
				}
			}, "PipeStdErr");
			
			// Pipe input to process
			Metabuild.get().setConsoleInputTarget(process.getOutputStream());
			
			// Start pipe threads
			stoutPipe.setDaemon(true);
			sterrPipe.setDaemon(true);
			stoutPipe.start();
			sterrPipe.start();
			
			// Wait for process to finish
			boolean aborted = false;
			while (process.isAlive()) {
				process.waitFor(1, TimeUnit.SECONDS);
				if (abortSwitch.get()) {
					process.destroy();
					aborted = true;
				}
			}
			
			int exitCode = aborted ? Integer.MIN_VALUE : process.exitValue();
			
			// Close pipes
			Metabuild.get().setConsoleInputTarget(null);
			process.getInputStream().close();
			process.getOutputStream().close();
			stoutPipe.join();
			sterrPipe.join();
			
			return exitCode;
		} catch (IOException e) {
			throw BuildException.msg(e, "failed to start process!");
		} catch (InterruptedException e) {
			throw BuildException.msg(e, "interrupted while waiting for process!");
		}
		
	}
	
}
