/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.JavaTestCase
import org.scalaide.debug.internal.expression.TestValues.any2String

class ModificationOfJavaStaticFieldsTest extends BaseIntegrationTest(ModificationOfJavaStaticFieldsTest) {

  @Test
  def changeStaticFieldsOfClass(): Unit = {
    eval("JavaLibClass.staticString = Int.MaxValue.toString; JavaLibClass.staticString", Int.MaxValue, Java.boxed.String)
    eval("JavaLibClass.staticInt -= 2 + 2; JavaLibClass.staticInt", JavaTestCase.JavaLibClass.staticInt - 4, Java.boxed.Integer)
  }

  @Test
  def changeStaticFieldsOfInnerStaticClass(): Unit = {
    eval("""JavaLibClass.InnerStaticClass.staticString = "bar"; JavaLibClass.InnerStaticClass.staticString""", "bar", Java.boxed.String)
    eval("""JavaLibClass.InnerStaticClass.staticString = "baz" + JavaLibClass.InnerStaticClass.staticString; JavaLibClass.InnerStaticClass.staticString""",
      "bazbar", Java.boxed.String)
    eval("JavaLibClass.InnerStaticClass.innerStaticDouble = -42; JavaLibClass.InnerStaticClass.innerStaticDouble", -42.0, Java.boxed.Double)
  }

  @Test
  def changeStaticFieldOfInnerStaticClassOfInnerStaticClass(): Unit = {
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt = 123; JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt",
      123, Java.boxed.Integer)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt = -110", Scala.unitLiteral, Scala.unitType)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt", -110, Java.boxed.Integer)
  }
}

object ModificationOfJavaStaticFieldsTest extends BaseIntegrationTestCompanion(JavaTestCase)