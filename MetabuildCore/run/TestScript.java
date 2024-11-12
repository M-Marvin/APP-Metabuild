
import java.io.File;

import javax.management.RuntimeErrorException;

import de.m_marvin.metabuild.core.exception.BuildScriptException;
import de.m_marvin.metabuild.core.script.BuildScript;
import de.m_marvin.metabuild.tasks.JavaCompileTask;

public class Buildfile extends BuildScript {
	
	public void init() {
		
		var compileJava = new JavaCompileTask("compileJava");
		
	}
	
}
