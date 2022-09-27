/*
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
package io.openmessaging.benchmark.utils;


import java.util.ArrayList;
import java.util.List;

public class ListPartition {

    /**
     * partition a list to specified size.
     *
     * @param originList
     * @param size
     * @param <T>
     * @return the partitioned list
     */
    public static <T> List<List<T>> partitionList(List<T> originList, int size) {

        List<List<T>> resultList = new ArrayList<>();
        if (null == originList || 0 == originList.size() || size <= 0) {
            return resultList;
        }
        if (originList.size() <= size) {
            for (T item : originList) {
                List<T> resultItemList = new ArrayList<>();
                resultItemList.add(item);
                resultList.add(resultItemList);
            }
            for (int i = 0; i < (size - originList.size()); i++) {
                resultList.add(new ArrayList<>());
            }
            return resultList;
        }

        for (int i = 0; i < size; i++) {
            resultList.add(new ArrayList<>());
        }
        int count = 0;
        for (T item : originList) {
            int index = count % size;
            resultList.get(index).add(item);
            count++;
        }
        return resultList;
    }
}
