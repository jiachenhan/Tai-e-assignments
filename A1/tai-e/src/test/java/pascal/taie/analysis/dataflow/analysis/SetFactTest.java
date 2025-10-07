package pascal.taie.analysis.dataflow.analysis;

import org.junit.Test;
import pascal.taie.util.collection.Sets;

import java.util.Collections;

public class SetFactTest {
    @Test
    public void test1() {
        var s1 = Sets.newHybridSet(); s1.add(1); s1.add(2);
        var s2 = Sets.newHybridSet(s1);      // 构造时 addAll(s1)

        s1.add(42);
        System.out.println(s2.contains(42)); // true ⇒ 共享可变后端；false ⇒ 深拷/或 COW
    }

    @Test
    public void testHybridEmpty() {
        var a = Sets.newHybridSet();   // 空
        var b = Sets.newHybridSet();   // 空

        a.add(1);
        System.out.println(b.contains(1));   // true ⇒ 共享 EMPTY 且无 COW；false ⇒ 首写分离或各自独立
    }

    @Test
    public void testEmpty() {
        var a = Sets.newHybridSet(Collections.emptySet());

        var b = Sets.newHybridSet(Collections.emptySet());

        a.add(1);
        System.out.println(b.contains(1));   // true ⇒ 共享 EMPTY 且无 COW；false ⇒ 首写分离或各自独立
    }
}
