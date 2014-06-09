package org.scalaide.debug.internal.model

import org.eclipse.debug.core.model.IVariable
import com.sun.jdi.LocalVariable
import com.sun.jdi.Field
import com.sun.jdi.ArrayType
import com.sun.jdi.ObjectReference
import org.eclipse.debug.core.model.IValue

abstract class ScalaVariable(target: ScalaDebugTarget) extends ScalaDebugElement(target) with IVariable {

  // Members declared in org.eclipse.debug.core.model.IValueModification

  override def setValue(value: IValue): Unit = ???
  override def setValue(value: String): Unit = ???
  override def supportsValueModification: Boolean = false // TODO: need real logic
  override def verifyValue(value: IValue): Boolean = ???
  override def verifyValue(value: String): Boolean = ???

  /** Remove private name mangling (taken from Scala compiler and adapted to work on Strings. */
  def unexpandedName(name: String): String = name lastIndexOf "$$" match {
    case 0 | -1 => name
    case idx0 =>
      // Sketchville - We've found $$ but if it's part of $$$ or $$$$
      // or something we need to keep the bonus dollars, so e.g. foo$$$outer
      // has an original name of $outer.
      var idx = idx0
      while (idx > 0 && name.charAt(idx - 1) == '$')
        idx -= 1
      name drop idx + 2
  }

  // Members declared in org.eclipse.debug.core.model.IVariable
  final override def getValue(): IValue =
    wrapJDIException("Exception while retrieving variable's value") { doGetValue() }

  final override def getName(): String =
    wrapJDIException("Exception while retrieving variable's name") { unexpandedName(doGetName()) }

  final override def getReferenceTypeName(): String =
    wrapJDIException("Exception while retrieving variable's reference type name") { doGetReferenceTypeName() }

  override def hasValueChanged: Boolean = false // TODO: need real logic

  def isStatic: Boolean = false
  def isFinal: Boolean = false
  protected def doGetValue(): IValue
  protected def doGetName(): String
  protected def doGetReferenceTypeName(): String
}

class ScalaThisVariable(underlying: ObjectReference, stackFrame: ScalaStackFrame) extends ScalaVariable(stackFrame.getDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  override protected def doGetName: String = "this"
  override protected def doGetReferenceTypeName: String = underlying.referenceType.name
  override protected def doGetValue: IValue = new ScalaObjectReference(underlying, getDebugTarget)
}

class ScalaLocalVariable(underlying: LocalVariable, stackFrame: ScalaStackFrame) extends ScalaVariable(stackFrame.getDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  override protected def doGetName(): String = underlying.name
  override protected def doGetReferenceTypeName(): String = underlying.typeName

  // fetching the value for local variables cannot be delayed because the underlying stackframe element may become invalid at any time
  override protected def doGetValue: IValue = ScalaValue(stackFrame.stackFrame.getValue(underlying), getDebugTarget)
}

class ScalaArrayElementVariable(index: Int, arrayReference: ScalaArrayReference) extends ScalaVariable(arrayReference.getDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  override protected def doGetName(): String = "(%s)".format(index)
  override protected def doGetReferenceTypeName(): String = arrayReference.underlying.referenceType.asInstanceOf[ArrayType].componentTypeName
  override protected def doGetValue(): IValue = ScalaValue(arrayReference.underlying.getValue(index), getDebugTarget)

}

class ScalaFieldVariable(field: Field, objectReference: ScalaObjectReference) extends ScalaVariable(objectReference.getDebugTarget) {

  // Members declared in org.eclipse.debug.core.model.IVariable

  override def isStatic: Boolean = field.isStatic()
  override def isFinal: Boolean = field.isFinal

  override protected def doGetName(): String = field.name
  override protected def doGetReferenceTypeName(): String = field.typeName
  override protected def doGetValue(): IValue = ScalaValue(objectReference.underlying.getValue(field), getDebugTarget)
}