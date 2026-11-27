# License

BastionVault is free and open-source software under the **MIT License**. You may use, copy, modify, merge, publish, distribute, sublicense and sell it, as long as the copyright and permission notice is kept in copies or substantial portions.

The license file is [`LICENSE`](https://github.com/Sarthak-G0yal/bastionvault/blob/main/LICENSE) in the repository.

```text
MIT License

Copyright (c) 2026 Sarthak Goyal

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES
OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

## No warranty

As the license says, the software comes **as is**. BastionVault has not had an independent security audit. Read the [security model](/security/model) to see what it protects and what it does not, and decide whether that fits what you store in it.

## Third-party components

These are bundled with the app or this site, under their own licenses:

| Component | Used for | License |
| :--- | :--- | :--- |
| [Geist and Geist Mono](https://vercel.com/font) | Typefaces in the app and this site | SIL Open Font License 1.1 (`fonts/GEIST-LICENSE.txt`) |
| [Lucide](https://lucide.dev) | Icons in the app and this site | ISC (`icons/LUCIDE-LICENSE.txt`) |
| [unslop](https://x.com/heydamir) by Damir Sokolovsky | Process used to design the interface | CC BY 4.0 |

Runtime dependencies are pulled by Maven and keep their own licenses. Among them: Spring Boot and Spring Security (Apache 2.0), Vaadin (Apache 2.0), the PostgreSQL JDBC driver (BSD 2-Clause), Flyway Community (Apache 2.0), and SnakeYAML (Apache 2.0). For the full list, run:

```bash
./mvnw license:aggregate-third-party-report
```

The site itself is built with [VitePress](https://vitepress.dev) (MIT).

## Contributions

Contributions are accepted under the same MIT License. See [Contributing](/project/contributing).
