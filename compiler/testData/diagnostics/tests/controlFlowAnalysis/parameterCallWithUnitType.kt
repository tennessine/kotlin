interface G

object F : G {
    fun x(f: G.() -> Unit) {
        f() // used!
    }
    fun y(f: G.() -> Unit) {
        this.f() // used!
    }
    fun z(f: (G) -> Unit) {
        f(this) // used!
    }
}

