package de.m_marvin.metabuild.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import de.m_marvin.metabuild.core.Metabuild;
import de.m_marvin.metabuild.core.exception.BuildException;
import de.m_marvin.simplelogging.api.Logger;

public class ProcessUtility {

	private ProcessUtility() {}
	
	public static int runProcess(Logger logger,  ProcessBuilder processBuilder) throws BuildException {
		
		try {
			// Start process
			Process process = processBuilder.start();
			
			// Pipe output to logger
			Thread stoutPipe = new Thread(() -> {
				BufferedReader source = new BufferedReader(new InputStreamReader(process.getInputStream()));
				PrintWriter target = logger.infoPrinterRaw();
				String line;
				try {
					while ((line = source.readLine()) != null) {
						target.println(line);
					}
					source.close();
					target.close();
				} catch (IOException e) {}
			}, "PipeStdOut");
			Thread sterrPipe = new Thread(() -> {
				BufferedReader source = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				PrintWriter target = logger.errorPrinterRaw();
				String line;
				try {
					while ((line = source.readLine()) != null) {
						target.println(line);
					}
					source.close();
					target.close();
				} catch (IOException e) {}
			}, "PipeStdErr");
			
			// Pipe input to process
			Metabuild.get().setConsoleInputTarget(process.getOutputStream());
			
			// Start pipe threads
			stoutPipe.setDaemon(true);
			sterrPipe.setDaemon(true);
			stoutPipe.start();
			sterrPipe.start();
			
			// Wait for process to finish
			int exitCode = process.waitFor();
			
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
