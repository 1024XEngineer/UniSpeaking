from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit


DIST = Path(__file__).resolve().parents[1] / "dist"


class ExpoPreviewHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(DIST), **kwargs)

    def do_GET(self):
        parsed = urlsplit(self.path)
        route = unquote(parsed.path).rstrip("/")
        if route and not Path(route).suffix:
            route_file = DIST / f"{route.lstrip('/')}.html"
            if route_file.is_file():
                self.path = f"{route}.html"
            else:
                self.path = "/index.html"
        return super().do_GET()


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8091), ExpoPreviewHandler)
    print("Serving Expo Web preview at http://localhost:8091")
    server.serve_forever()
