package org.scalaide.debug.internal.romanowski

import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.{After, AfterClass, Before, Test}
import org.scalaide.core.testsetup.{SDTTestUtils, TestProjectSetup}
import org.scalaide.debug.internal._
import org.junit.Assert._

object SimplePingTest extends TestProjectSetup("mgr-benchmarks",srcRoot = "/%s/src/main/scala", bundleName = "org.scala-ide.sdt.debug.tests") with ScalaDebugRunningTest {
  var initialized = false

  def initDebugSession(launchConfigurationName: String): ScalaDebugTestSession = ScalaDebugTestSession(file(launchConfigurationName + ".launch"))

  @AfterClass
  def deleteProject(): Unit = {
    SDTTestUtils.deleteProjects(project)
  }
}
@Test
class SimplePingTest {

  import org.scalaide.debug.internal.romanowski.SimplePingTest._

  var session: ScalaDebugTestSession = null

  @Before
  def initializeTests(): Unit = {
    SDTTestUtils.enableAutoBuild(false)
    if (!initialized) {
      project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
      project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
      initialized = true
    }
  }

  @After
  def cleanDebugSession(): Unit = {
    if (session ne null) {
      session.terminate()
      session = null
    }
  }

  private def runTestForGivenApplication(name: String): Unit = {
    session = initDebugSession(name)
    val benchmarkTypeName = "pl.typosafe.mgr.benchmark.Benchmark"
    val lineNr = 19
    session.runToLine(benchmarkTypeName, lineNr)

    val values = session.currentStackFrame.stackFrame.getArgumentValues
    val success = values.get(1).toString().toBoolean
    val message = values.get(0).toString()
    assertTrue("Run faild. Reason: " + message, success)
    
    analyze(message);
  }
  
  private def analyze(result: String): Unit = {
    println(result);
  }


  @Test
  def simpleBreakpointEnableDisable(): Unit = {
    runTestForGivenApplication("SimplePing16Threads")
  }
}
