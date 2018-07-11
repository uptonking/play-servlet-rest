/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.tablesaw.columns;

import it.unimi.dsi.fastutil.ints.IntIterable;
import tech.tablesaw.api.IntColumn;
import tech.tablesaw.filtering.IntBiPredicate;
import tech.tablesaw.filtering.IntPredicate;
/**
 * int 列 工具类
 */
public interface IntColumnUtils extends Column, IntIterable {

    IntPredicate isZero = i -> i == 0;

    IntPredicate isNegative = i -> i < 0;

    IntPredicate isPositive = i -> i > 0;

    IntPredicate isNonNegative = i -> i >= 0;

    IntPredicate isEven = i -> (i & 1) == 0;

    IntPredicate isOdd = i -> (i & 1) != 0;

    IntBiPredicate isGreaterThan = (valueToTest, valueToCompareAgainst) -> valueToTest > valueToCompareAgainst;
    IntBiPredicate isGreaterThanOrEqualTo = (valueToTest, valueToCompareAgainst) -> valueToTest >=
            valueToCompareAgainst;

    IntBiPredicate isLessThan = (valueToTest, valueToCompareAgainst) -> valueToTest < valueToCompareAgainst;
    IntBiPredicate isLessThanOrEqualTo = (valueToTest, valueToCompareAgainst) -> valueToTest <= valueToCompareAgainst;

    IntBiPredicate isEqualTo = (valueToTest, valueToCompareAgainst) -> valueToTest == valueToCompareAgainst;
    IntBiPredicate isNotEqualTo = (valueToTest, valueToCompareAgainst) -> valueToTest != valueToCompareAgainst;

    IntPredicate isMissing = i -> i == IntColumn.MISSING_VALUE;

    IntPredicate isNotMissing = i -> i != IntColumn.MISSING_VALUE;
}