package org.scalaide.debug.internal.async

import com.sun.jdi.ReferenceType
import com.sun.jdi.Method
import scala.collection.JavaConverters._
import org.scalaide.debug.internal.model.JdiRequestFactory
import org.scalaide.debug.internal.model.ScalaDebugTarget
import org.scalaide.logging.HasLogger
import scala.actors.Actor
import com.sun.jdi.request.EventRequest

object Utility extends HasLogger {

  def installMethodBreakpoint(debugTarget: ScalaDebugTarget, app: AsyncProgramPoint, actor: Actor): Seq[EventRequest] = {
    def isAPP(m: Method): Boolean =
      (!m.isAbstract()
        && m.name().startsWith(app.methodName)
        && !m.name().contains("$default"))

    for {
      tpe <- debugTarget.virtualMachine.classesByName(app.className).asScala.toSeq
      method <- tpe.allMethods().asScala.find(isAPP)
    } yield {
      val req = JdiRequestFactory.createMethodEntryBreakpoint(method, debugTarget)
      debugTarget.eventDispatcher.setActorFor(actor, req)
      req.putProperty("app", app)
      req.enable()

      logger.debug(s"Installed method breakpoint for ${method.declaringType()}.${method.name}")
      req
    }
  }
}