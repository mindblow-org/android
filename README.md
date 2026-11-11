<p align="center">
  <img src="docs/images/icon.svg" width="88" height="88" alt="">
</p>

<h1 align="center">mindblow para Android</h1>

<p align="center">O <a href="https://github.com/mindblow-org/desktop">mindblow</a> no celular, como app: notas no próprio aparelho, sync com os outros, atalhos, widget e "compartilhar".</p>

<p align="center"><a href="https://mindblow-org.github.io/site/">Site</a> · <a href="https://github.com/mindblow-org/android/releases/latest/download/mindblow-android.apk">Baixar o APK</a></p>

> [!WARNING]
> **Ainda não testado no celular.** O servidor dentro do app (`libmindblow.so`) e o sync foram testados no PC, mas esta versão do app ainda precisa ser validada num aparelho de verdade, com o cabo plugado: abrir sem internet, criar notas, ler o QR do PC, ver as notas irem e voltarem, compartilhar de outro app e o widget. Veja [Testar no aparelho](#testar-no-aparelho).

O app leva o mindblow inteiro junto: o mesmo servidor do desktop, compilado para Android, roda dentro dele com o banco no próprio celular. A WebView abre a interface que esse servidor entrega, então estilo e comportamento são os do navegador, e tudo funciona sem internet. O que o Android ganha fica em volta:

- **Sync com outros aparelhos** (opcional): toque no QR do canto de cima, em "ler QR", e leia o QR do mindblow do PC (ou cole o link). Daí em diante, o que muda aqui vai para lá e o que muda lá vem para cá, sempre que o outro aparelho responder. Sem rede, as mudanças esperam.
- **Compartilhar**: texto e imagens de outros apps viram nota. Texto curto vira sticky; com assunto (o título da página, no navegador), mais de 500 caracteres ou várias imagens, vira texto. As imagens são reduzidas como na web: lado maior de até 2048px, em WebP.
- **Atalhos**: segure o ícone para criar uma sticky, um texto, um todo ou um board.
- **Widget**: uma barra na tela inicial com o logo, que abre o app, e um botão para cada tipo de nota.
- **Voltar**: fecha o que estiver aberto (nota, busca, seletor, imagem) antes de sair do app.

Quem já usava o app conectado ao PC não precisa fazer nada: na primeira vez, o link guardado vira o sync com o PC, e as notas de lá descem para o celular.

## Instalar

Baixe o [`mindblow-android.apk`](https://github.com/mindblow-org/android/releases/latest/download/mindblow-android.apk) no celular e abra. O Android pede para liberar a instalação pelo navegador na primeira vez. Precisa de Android 9 ou mais novo. Para sincronizar com o PC pela tailnet, o Tailscale precisa estar ligado no celular.

O leitor de QR é o do Google Play Services, que não pede permissão de câmera. Sem ele, cole o link.

## Como funciona

```
app/src/main/java/app/mindblow/android/
  Node.kt     sobe o servidor (libmindblow.so) com o banco do app
  Main.kt     WebView, tela de erro, voltar, escolher arquivo, ler QR
  Share.kt    recebe o "compartilhar" e salva no servidor daqui
  Pic.kt      reduz a imagem antes de enviar (igual a web/src/lib/images/shrink.ts)
  Server.kt   chamadas à API do servidor daqui
  Widget.kt   barra da tela inicial
```

- `libmindblow.so` é o crate `mobile` do [mindblow](https://github.com/mindblow-org/desktop): o servidor inteiro, com a interface embutida, e uma função JNI só (`Node.start`). O app passa banco, porta e token por variáveis de ambiente (`Os.setenv`), como o serviço do PC.
- O servidor escuta em `127.0.0.1:7341`, e o banco fica em `files/mindblow.db`. A porta é fixa porque o `localStorage` da WebView (abas, nome do aparelho) é por endereço.
- `127.0.0.1` é o mesmo para todos os apps do celular, então a API exige um token aleatório, criado na instalação. A WebView entra com ele no `#token=` do link, e o compartilhar usa o mesmo.
- O sync é do servidor: ele puxa e empurra as mudanças de cada link e troca as imagens que faltam. Mudança aqui ou lá acorda o sync; sem resposta, ele tenta de novo com espera crescente. Enquanto o app está vivo, o sync roda; fechado, ele recomeça na próxima vez que o app abrir.
- A interface pede para ler um QR por `window.mindblowAndroid.scan()`, e o app devolve o link em `window.mindblowLink(url)`. O leitor é o do Google Play Services, que não pede permissão de câmera.
- Atalhos, widget e compartilhar chegam ao `Main` como extra e viram hash na URL: `#new=<tipo>` cria e abre uma nota, `#open=<id>` abre uma. Quem trata é a interface (`web/src/view/Main.svelte` no mindblow).
- O botão voltar chama `window.mindblowBack()`, que faz o papel do Esc e responde se havia algo para fechar. Se não havia, o app vai para segundo plano.
- O app se apresenta com `mindblow-android/<versão>` no user agent, e a lista de dispositivos mostra "App Android".
- Do Android 11 em diante, o app desenha por baixo das barras do sistema e põe o recuo à mão, contando o teclado.
- Sem AndroidX nem Compose: `Activity`, `WebView` e `HttpURLConnection` do próprio Android. A única dependência Java é o leitor de QR.

## Desenvolver

Precisa do JDK 17, do Android SDK (35) com o NDK 27.1.12297006, do Rust com as targets do Android, do [`cargo-ndk`](https://github.com/bbqsrc/cargo-ndk) e do pnpm, e do repo do [mindblow](https://github.com/mindblow-org/desktop) ao lado (`../mindblow.app`) ou em `MINDBLOW_SRC`:

```sh
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk

./gradlew assembleDebug                 # compila a interface, a libmindblow.so e o APK
./gradlew installDebug                  # instala no aparelho ou emulador ligado
./gradlew lintDebug
MINDBLOW_SKIP_NODE=1 ./gradlew assembleDebug   # só o Kotlin, sem o servidor (o APK não abre)
```

No emulador, o PC é `10.0.2.2`. Para sincronizar com um mindblow rodando nele, suba o servidor com `MINDBLOW_HOSTS=10.0.2.2` e ligue o link `http://10.0.2.2:7341` no app.

## Testar no aparelho

Com o celular plugado (`adb devices`) e o mindblow com sync rodando no PC:

1. `./gradlew installDebug` e abra pelo ícone. Com o modo avião ligado, o app abre direto nas notas, sem tela de conectar.
2. Crie uma nota de cada tipo, com imagem, ainda sem rede. Feche e abra: elas continuam lá.
3. Desligue o modo avião, ligue o Tailscale, toque no QR, em "ler QR", e leia o QR do mindblow do PC. O link aparece "em dia" e as notas vão para o PC (com as imagens).
4. Edite a mesma nota no PC e no celular, uma em cada campo, e confira que as duas edições ficam nos dois lados. Exclua uma no PC e veja ela sumir do celular.
5. Compartilhe um texto e uma foto de outro app, use os atalhos do ícone e o widget, com e sem rede.
6. Quem já tinha o app conectado ao PC: instale por cima (`adb install -r`) e veja o link antigo virar o sync, com as notas descendo.

Em aparelho MediaTek (como o Moto G24), não abra o app com `am start` logo depois de um `am force-stop`: o DuraSpeed deixa a WebView em branco. Abra pelo ícone.

## Release

Cada push na branch `release` dispara o workflow [release](.github/workflows/release.yml), que compila o APK assinado e publica a release `vX.Y.Z` com o `mindblow-android.apk`. A versão vem de `version` no `gradle.properties` (o `versionCode` sai dela: `1.2.3` vira `10203`). Para soltar uma versão nova:

```sh
# suba a versão no gradle.properties e commite na main
git push origin main:release
```

Se a versão já foi publicada, o workflow para e avisa. A assinatura usa quatro secrets do repositório:

| Secret | |
|---|---|
| `ANDROID_KEYSTORE` | o `.jks` em base64 (`base64 -w0 release.jks`) |
| `ANDROID_KEYSTORE_PASSWORD` | senha do keystore |
| `ANDROID_KEY_ALIAS` | alias da chave |
| `ANDROID_KEY_PASSWORD` | senha da chave |

Guarde o keystore fora do repositório: sem ele, um APK novo não instala por cima do antigo. Localmente, sem `MINDBLOW_KEYSTORE`, o `assembleRelease` assina com a chave de debug, só para testar.

## Licença

[MIT](LICENSE)
