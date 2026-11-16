# Contributing to UNICUM

Thanks for helping! Issues and pull requests are welcome. *(Italiano più sotto.)*

## Good places to start

- **English translation**: the UI is Italian; strings need to move to `strings.xml`.
- **New PDF plan formats**: open an issue and attach an *anonymised* example of the plan.
- **Other devices**: tell us how the app behaves with your watch or ring through Health Connect.

## Before you open a pull request

- Build and run the tests: `cd codice && ./gradlew testDebugUnitTest assembleDebug`.
- Keep the pure logic in `data/` covered by unit tests.
- Never commit personal data: health data, Notion IDs, tokens, `personal.properties`, `keystore.properties` or keystores.
- Changing the Room schema needs a `Migration` and a new database version, because people have real data in the app.
- Follow the existing style: Material 3, the shared components in `ui/components/UnicumUi.kt`, light and dark theme tokens instead of fixed colours, Montserrat without bold and rounded Material icons (palette and mood in `grafiche/stile grafico.txt`).

## License of your contribution

By submitting a contribution (code, text, images or anything else) you agree that:

1. it is your own work, or you have the right to submit it;
2. it is licensed to everyone under the project's license, the **GNU GPL version 3 or later**, including the additional permission in [`NOTICE.md`](NOTICE.md);
3. you also grant the project's author (the copyright holder named in `NOTICE.md`) a perpetual, worldwide, non-exclusive, royalty-free, irrevocable license to use, modify, sublicense and distribute your contribution **under other license terms as well**, including proprietary ones, for example to publish the app in an app store or to change the project's license in the future.

You keep the copyright on your contribution. If you don't agree with point 3, say so in the pull request before it is merged.

---

## 🇮🇹 In breve

- Prima di una pull request: compila, esegui i test e non inserire mai dati personali.
- **Licenza dei contributi**: quello che invii è distribuito con licenza GPL-3.0 o successiva. In più dai all'autore del progetto il permesso di usarlo anche con altre licenze, comprese quelle chiuse, per esempio per pubblicare l'app su uno store o cambiare licenza in futuro. Il copyright resta tuo.
