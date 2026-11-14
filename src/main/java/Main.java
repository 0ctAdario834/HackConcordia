import io.javalin.Javalin;

void main() {
    Javalin app = Javalin.create().start(7000);
    app.get("/", ctx -> ctx.result("Hello World"));
    app.start();
}
