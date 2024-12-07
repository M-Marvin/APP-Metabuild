
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import javax.management.RuntimeErrorException;

import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.tasks.JavaCompileTask;

public class Buildfile extends BuildScript {
	
	public void init() {
		
		var compileJava = new JavaCompileTask("compileJava");
		
	}
	
}
