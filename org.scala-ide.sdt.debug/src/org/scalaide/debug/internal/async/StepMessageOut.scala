package org.scalaide.debug.internal.async

import org.scalaide.debug.internal.BaseDebuggerActor
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.debug.internal.model.ScalaThread
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.eclipse.debug.core.DebugEvent
import com.sun.jdi.event.BreakpointEvent
import org.scalaide.logging.HasLogger
import com.sun.jdi.ObjectReference
import com.sun.jdi.request.EventRequest
import com.sun.jdi.event.StepEvent
import com.sun.jdi.request.StepRequest

case class StepMessageOut(debugTarget: ScalaDebugTarget, thread: ScalaThread) extends HasLogger {

  private var watchedMessage: Option[ObjectReference] = None
  private val senderFrameLocation = thread.threadRef.frame(0).location

  val programSends = List(
    AsyncProgramPoint("akka.actor.RepointableActorRef", "$bang", 0),
    AsyncProgramPoint("scala.actors.InternalReplyReactor$class", "$bang", 1))

  val programReceives = List(
    AsyncProgramPoint("akka.actor.ActorCell", "receiveMessage", 0))

  private var sendRequests = Set[EventRequest]()
  private var receiveRequests = Set[EventRequest]()
  private var stepRequests = Set[EventRequest]()
  private var steps = 0

  def step() {
    sendRequests = programSends.flatMap(Utility.installMethodBreakpoint(debugTarget, _, internalActor)).toSet
    receiveRequests = programReceives.flatMap(Utility.installMethodBreakpoint(debugTarget, _, internalActor)).toSet
    internalActor.start()
    // CLIENT_REQUEST seems to be the only event that correctly updates the UI
    thread.resumeFromScala(DebugEvent.CLIENT_REQUEST)
  }

  object internalActor extends BaseDebuggerActor {
    import scala.collection.JavaConverters._

    private def interceptMessage(ev: BreakpointEvent): Boolean = {
      // only intercept messages going out from the current thread and frame (no messages sent by methods below us)
      if (sendRequests(ev.request)
        && (ev.thread() == thread.threadRef)) {
        println("? intercept send: " + ev.thread.frame(0).getArgumentValues())
        ev.thread.frames.asScala.take(15).foreach { f =>
          logger.debug(s"\t${f.location}")
        }
        true
      } else false
      //        && (ev.thread.frame(1).location == senderFrameLocation))
    }

    override protected def behavior = {
      case breakpointEvent: BreakpointEvent if interceptMessage(breakpointEvent) =>
        val app = breakpointEvent.request().getProperty("app").asInstanceOf[AsyncProgramPoint]
        val topFrame = breakpointEvent.thread().frame(0)
        val args = topFrame.getArgumentValues()
        logger.debug(s"MESSAGE OUT intercepted: topFrame arguments: $args")
        watchedMessage = Option(args.get(app.paramIdx).asInstanceOf[ObjectReference])

        reply(false) // don't suspend this thread
      //        logger.debug(s"Suspending thread ${breakpointEvent.thread.name()}")
      //        // most likely the breakpoint was hit on a different thread than the one we started with, so we find it here
      //        debugTarget.getScalaThread(breakpointEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
      //        reply(true) // suspend here!

      case breakpointEvent: BreakpointEvent if receiveRequests(breakpointEvent.request()) =>
        val app = breakpointEvent.request().getProperty("app").asInstanceOf[AsyncProgramPoint]
        val topFrame = breakpointEvent.thread().frame(0)
        val args = topFrame.getArgumentValues()
        logger.debug(s"receive intercepted: topFrame arguments: $args")
        val msg = Option(args.get(app.paramIdx).asInstanceOf[ObjectReference])
        if (watchedMessage == msg) {
          logger.debug(s"MESSAGE IN! $msg")

          val targetThread = debugTarget.getScalaThread(breakpointEvent.thread())
          targetThread foreach { thread =>
            val stepReq = JdiRequestFactory.createStepRequest(StepRequest.STEP_LINE, StepRequest.STEP_INTO, thread)
            stepReq.enable()
            stepRequests = Set(stepReq)
            debugTarget.eventDispatcher.setActorFor(this, stepReq)
            steps = 0
          }
        }
        reply(false)

      case stepEvent: StepEvent if stepEvent.location().method().name().contains("applyOrElse") =>
        disable()
        poison()
        logger.debug(s"Suspending thread ${stepEvent.thread.name()}")
        // most likely the breakpoint was hit on a different thread than the one we started with, so we find it here
        debugTarget.getScalaThread(stepEvent.thread()).foreach(_.suspendedFromScala(DebugEvent.BREAKPOINT))
        reply(true) // suspend here!

      case stepEvent: StepEvent =>
        //        logger.debug(s"Step $steps in ${stepEvent.location().method().name()}")
        steps += 1
        reply(false) // resume VM
      case _ => reply(false)
    }

    private def disable() {
      val eventDispatcher = debugTarget.eventDispatcher
      val eventRequestManager = debugTarget.virtualMachine.eventRequestManager

      for (request <- sendRequests ++ receiveRequests ++ stepRequests) {
        request.disable()
        eventDispatcher.unsetActorFor(request)
        eventRequestManager.deleteEventRequest(request)
      }
    }
  }
}