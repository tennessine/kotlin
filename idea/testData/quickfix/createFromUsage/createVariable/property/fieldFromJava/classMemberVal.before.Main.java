// "Add field 'foo' to 'K'" "true"
class J {
    void test(K k) {
        String s = k.<caret>foo;
    }
}