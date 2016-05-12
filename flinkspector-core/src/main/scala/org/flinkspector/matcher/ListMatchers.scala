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
package org.flinkspector.matcher

import org.hamcrest.Description
import org.scalatest.Matchers
import org.scalatest.enablers.{Aggregating, Sequencing}

/**
  * Wrapper around the [[Matchers]] library from ScalaTest.
  *
  * @see http://scalatest.org/
  *      Offers several methods to startWith different [[ListMatcher]]s working on lists.
  */
object ListMatchers extends Matchers {


  /**
    * Provides a [[ListMatcher]] to tests whether a list contains only a set of elements.
    *
    * @example List(1,2,3,4) matched against List(1,2,3) is not valid.
    * @param right expected list of elements
    * @tparam T type to match
    * @return concrete [[ListMatcher]]
    */
  def containsOnly[T](right: List[T])(implicit aggregating: Aggregating[List[T]])
  : ListMatcher[T] = {
    new ListMatcher[T](right) {
      override def matchesSafely(left: List[T]): Boolean = {

        if (aggregating.containsOnly(left, right.distinct)) {
          ensureFrequency(_ < _)(left, right)
        } else {
          false
        }
      }

      override def toString: String = "only matcher"

      override def describeTo(description: Description): Unit = {
        description.appendText("contains only")
      }
    }
  }

  /**
    * Provides a [[ListMatcher]] to tests whether a list contains a set of elements.
    *
    * @example List(1,3,2,4) matched against List(1,2,3) is valid.
    * @param right expected list of elements.
    * @tparam T type to match
    * @return concrete [[ListMatcher]]
    */
  def containsAll[T](right: List[T])(implicit aggregating: Aggregating[List[T]])
  : ListMatcher[T] = {
    new ListMatcher[T](right) {
      override def matchesSafely(left: List[T]): Boolean = {

        if (aggregating.containsAllOf(left, right)) {
          ensureFrequency(_ < _)(left, right)
        } else {
          false
        }

      }

      override def toString: String = "all matcher"

      override def describeTo(description: Description): Unit = {
        description.appendText("contains all")
      }

    }
  }

  /**
    * Provides a [[ListMatcher]] to tests whether a list contains a sequence of elements.
    * The matcher permits other elements between the ordered elements.
    * also allows for duplicates.
    *
    * @example List(1,2,4,3,3,5) matched against List(1,2,3) is valid.
    * @param right expected order of elements
    * @tparam T type to match
    * @return concrete [[ListMatcher]]
    */
  def containsInOrder[T](right: List[T])(implicit sequencing: Sequencing[List[T]])
  : ListMatcher[T] = {
    new ListMatcher[T](right) {
      override def matchesSafely(left: List[T]): Boolean =
        sequencing.containsInOrder(left, right)

      override def toString: String = "order matcher"

      override def describeTo(description: Description): Unit = {
        description.appendText("in order ")
        describeOutput(right, description)
      }
    }
  }


  /**
    * Provides a [[ListMatcher]] to tests whether a list contains another list
    *
    * @example List(1,2,3,4) matched against List(2,3) is valid.
    * @param right expected list
    * @tparam T type to match
    * @return concrete [[ListMatcher]]
    */
  def containsInSeries[T](right: List[T]): ListMatcher[T] = {
    new ListMatcher[T](right) {
      override def matchesSafely(left: List[T]): Boolean =
        left.containsSlice(right)

      override def toString: String =
        "series matcher"

      override def describeTo(description: Description): Unit = {
        description.appendText("in series ")
        describeOutput(right, description)
      }
    }
  }

  /**
    * Provides a [[ListMatcher]] to tests whether a list contains
    * an element with the same number of occurrences.
    *
    * @example List(1,2,2,3,4,4) matched against List(1,2,2) is valid.
    * @param right expected list
    * @tparam T type to match
    * @return concrete [[ListMatcher]]
    */
  def sameFrequency[T](right: List[T]): ListMatcher[T] = {
    new ListMatcher[T](right) {
      override def matchesSafely(left: List[T]): Boolean =
        ensureFrequency(_ != _)(left, right)

      override def toString: String = {
        "frequency matcher"
      }

      override def describeTo(description: Description): Unit = {
        description.appendText("same frequency")
      }
    }
  }

  private def describeOutput[T](list: Seq[T], description: Description) = {
    val builder = StringBuilder.newBuilder
    builder.append("<[")
    builder.append(list.mkString(", "))
    builder.append("]>")
    description.appendText(builder.toString())
  }

  /**
    * Helper function to split a list into a [[Tuple3]].
    *
    * @param list to split
    * @return (first element, second element, rest of elements)
    */
  private def splitTo(list: List[Any]): (Any, Any, List[Any]) = {
    (list.head, list.tail.head, list.tail.tail)
  }

  /**
    * Helper function to compare the frequency of elements in lists.
    *
    * @param c     function which compares the frequency of the elements.
    * @param left  first list.
    * @param right second list.
    * @tparam T generic type of the lists.
    * @return true if comparator is true for each element else false.
    */
  private def ensureFrequency[T](c: (Int, Int) => Boolean)(left: List[T], right: List[T]): Boolean = {
    val countDuplicates = (l: List[T]) => l
      .groupBy(identity)
      .mapValues(_.size)

    val leftDuplicates = countDuplicates(left)
    val rightDuplicates = countDuplicates(right)

    rightDuplicates.foreach {
      case (elem, count) =>
        if (leftDuplicates.contains(elem)) {
          if (c(leftDuplicates(elem), count)) {
            return false
          }
        }
    }
    true
  }
}
