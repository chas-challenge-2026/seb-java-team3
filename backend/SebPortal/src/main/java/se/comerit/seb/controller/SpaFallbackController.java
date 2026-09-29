package se.comerit.seb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// SPA-fallback: React-appen sköter routingen i webbläsaren, men vid omladdning eller en
// direktlänk (t.ex. /attest) frågar webbläsaren servern efter just den sökvägen. Den finns
// inte här, så svaret hade blivit 404. Istället forwardas requesten internt till index.html
// (adressfältet ändras inte), React startar och routern visar rätt vy.
//
// Mönstren släpper medvetet igenom två sorters sökvägar till Springs vanliga hantering:
//  - /api och /api/**: (?!api$) utesluter "api" som första segment, så API:t svarar alltid
//    som API (JSON, 401, 404) och aldrig med index.html.
//  - filer: [^.]+ tillåter ingen punkt, så t.ex. /assets/index-abc.js och /favicon.svg
//    serveras som statiska filer. Därför kan forwarden till /index.html inte heller loopa.
//
// Mer specifika mappningar i andra controllers vinner över de här mönstren, så det här är en
// ren fallback. Mönstren täcker en och två nivåer (/attest, /payments/new), vilket är alla
// routes i frontend/src/router.tsx. Spring 6 tillåter inte ** mitt i ett mönster, så en ny
// route med tre nivåer behöver ett mönster till här.
@Controller
public class SpaFallbackController {

    @GetMapping({
            "/{path:(?!api$)[^.]+}",
            "/{path:(?!api$)[^.]+}/{subpath:[^.]+}"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
