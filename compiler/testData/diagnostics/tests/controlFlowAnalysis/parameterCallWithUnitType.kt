interface G

object F : G {
    fun x(f: G.() -> Unit) {
        <!UNUSED_EXPRESSION!>f<!>() // used!
    }
    fun y(f: G.() -> Unit) {
        this.f() // used!
    }
    fun z(f: (G) -> Unit) {
        f(this) // used!
    }
}

