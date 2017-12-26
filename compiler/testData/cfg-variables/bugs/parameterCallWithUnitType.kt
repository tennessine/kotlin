object F {
    fun x(f: F.() -> Unit) {
        f()
    }
    fun y(f: F.() -> Unit) {
        this.f()
    }
}
class G {
    fun z(g: G.() -> Unit) {
        g()
    }
}
