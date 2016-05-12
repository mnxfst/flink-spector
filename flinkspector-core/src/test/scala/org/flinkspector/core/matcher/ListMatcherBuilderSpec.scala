/*
 * Copyright 2015 Otto (GmbH & Co KG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flinkspector.core.matcher

import org.flinkspector.core.CoreSpec
import org.flinkspector.matcher.ListMatcherBuilder

class ListMatcherBuilderSpec extends CoreSpec {

  "The ListMatcherBuilder" should "check for all per default" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.matches(List(1, 2, 3, 4)) shouldBe true
    builder.matches(List(1, 2, 3)) shouldBe false
    builder.matches(List(1, 2, 3, 4, 5)) shouldBe true
  }

  it should "check for all if only was not defined" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.sameFrequency()

    builder.matches(List(1, 2, 3, 4)) shouldBe true
    builder.matches(List(1, 2, 3)) shouldBe false
    builder.matches(List(1, 2, 3, 4, 5)) shouldBe true
  }

  it should "check for only if only was defined" in {
    val onlyBuilder = new ListMatcherBuilder[Int](List(1, 2, 3, 4)).only()

    onlyBuilder.matches(List(1, 2, 3, 4)) shouldBe true
    onlyBuilder.matches(List(1, 2, 3)) shouldBe false
    onlyBuilder.matches(List(1, 2, 3, 4, 5)) shouldBe false
  }

  it should "check for only if only was defined in combination" in {
    val onlyBuilder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
      .only()
      .sameFrequency()

    onlyBuilder.matches(List(1, 2, 3, 4)) shouldBe true
    onlyBuilder.matches(List(1, 2, 3)) shouldBe false
    onlyBuilder.matches(List(1, 2, 3, 4, 5)) shouldBe false
  }

  it should "check for order" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.inOrder().all()

    builder.matches(List(1, 2, 3, 4)) shouldBe true
    builder.matches(List(1, 2, 4, 3)) shouldBe false
  }

  it should "check for partial order" in {
    val fromToBuilder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    fromToBuilder.inOrder().from(1).to(2)

    fromToBuilder.matches(List(1, 2, 3, 4)) shouldBe true
    fromToBuilder.matches(List(1, 3, 2, 4)) shouldBe false

    val indicesBuilder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    indicesBuilder.inOrder().indices(0, 3)

    indicesBuilder.matches(List(1, 2, 3, 4)) shouldBe true
    indicesBuilder.matches(List(4, 3, 2, 1)) shouldBe false
  }

  it should "check for order in combination" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.only().inOrder().all()

    builder.matches(List(1, 2, 3, 4)) shouldBe true
    builder.matches(List(1, 2, 3, 4, 5)) shouldBe false
    builder.matches(List(1, 2, 4, 3)) shouldBe false
  }

  it should "check for series" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.inSeries().all()

    builder.matches(List(1, 2, 3, 4)) shouldBe true
    builder.matches(List(1, 2, 4, 3)) shouldBe false
  }

  it should "check for partial series" in {
    val fromToBuilder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    fromToBuilder.inSeries().from(1).to(2)

    fromToBuilder.matches(List(1, 2, 3, 4)) shouldBe true
    fromToBuilder.matches(List(1, 3, 2, 4)) shouldBe false

    val indicesBuilder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    indicesBuilder.inSeries().indices(0, 3)

    indicesBuilder.matches(List(1, 4, 2, 3)) shouldBe true
    indicesBuilder.matches(List(4, 2, 3, 1)) shouldBe false
  }

  it should "check for series in combination" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.only().inSeries().all()

    builder.matches(List(1, 2, 3, 4)) shouldBe true
    builder.matches(List(1, 2, 3, 4, 5)) shouldBe false
    builder.matches(List(1, 2, 4, 3)) shouldBe false
  }
  it should "check for duplicates" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.sameFrequency()

    builder.matches(List(1, 2, 3, 4, 5)) shouldBe true
    builder.matches(List(1, 2, 3, 4, 4)) shouldBe false
  }

  it should "check for duplicates in combination" in {
    val builder = new ListMatcherBuilder[Int](List(1, 2, 3, 4))
    builder.sameFrequency().only()

    builder.matches(List(1, 2, 3, 4)) shouldBe true
    builder.matches(List(1, 2, 3, 4, 5)) shouldBe false
    builder.matches(List(1, 2, 3, 4, 4)) shouldBe false
  }

}
