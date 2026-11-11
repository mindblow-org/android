<p align="center">
  <img src="docs/images/icon.svg" width="88" height="88" alt="">
</p>

<h1 align="center">mindblow para Android</h1>

<p align="center">O <a href="https://github.com/mindblow-org/mindblow">mindblow</a> no celular, como app: tela cheia, atalhos, widget e "compartilhar".</p>

<p align="center"><a href="https://mindblow-org.github.io/site/">Site</a> · <a href="https://github.com/mindblow-org/android/releases/latest/download/mindblow-android.apk">Baixar o APK</a></p>

O app é uma casca leve, como o app desktop: uma WebView que carrega a interface que o próprio servidor entrega. Estilo e comportamento são os mesmos do navegador; o que o Android ganha fica em volta:

- **Conectar por QR**: na primeira vez, leia o QR de "conectar outro aparelho" (o botão de QR no canto de cima do mindblow) ou cole o link da tailnet. O token do `#token=` vem junto.
- **Compartilhar**: texto e imagens de outros apps viram nota. Texto curto vira sticky; com assunto (o título da página, no navegador), mais de 500 caracteres ou várias imagens, vira texto. As imagens são reduzidas como na web: lado maior de até 2048px, em WebP.
- **Atalhos**: segure o ícone para criar uma sticky, um texto, um todo ou um board.
- **Widget**: uma barra na tela inicial com o logo, que abre o app, e um botão para cada tipo de nota.
- **Voltar**: fecha o que estiver aberto (nota, busca, seletor, imagem) antes de sair do app.

Sem conexão (Tailscale desligado, servidor fora do ar), aparece uma tela com "tentar de novo" e "trocar link".

## Instalar

Baixe o [`mindblow-android.apk`](https://github.com/mindblow-org/android/releases/latest/download/mindblow-android.apk) no celular e abra. O Android pede para liberar a instalação pelo navegador na primeira vez. Precisa de Android 9 ou mais novo e do Tailscale ligado no aparelho.

O leitor de QR é o do Google Play Services, que não pede permissão de câmera. Sem ele, cole o link.

## Como funciona

```
app/src/main/java/app/mindblow/android/
  Main.kt     WebView, tela de conectar e de sem conexão, voltar, escolher arquivo
  Share.kt    recebe o "compartilhar" e salva pela API
  Pic.kt      reduz a imagem antes de enviar (igual a web/src/lib/images/shrink.ts)
  Server.kt   link e token guardados no aparelho, chamadas à API
  Widget.kt   barra da tela inicial
```

- O link e o token ficam nas preferências do app. O token vem do QR ou, se você entrou pela tela de token da interface, é lido do `localStorage` da WebView (`mindblow.token`) a cada página carregada e quando o app sai da frente.
- Atalhos, widget e compartilhar chegam ao `Main` como extra e viram hash na URL: `#new=<tipo>` cria e abre uma nota, `#open=<id>` abre uma. Quem trata é a interface (`web/src/view/Main.svelte` no mindblow).
- O botão voltar chama `window.mindblowBack()`, que faz o papel do Esc e responde se havia algo para fechar. Se não havia, o app vai para segundo plano.
- O app se apresenta com `mindblow-android/<versão>` no user agent, e a lista de dispositivos mostra "App Android".
- Do Android 11 em diante, o app desenha por baixo das barras do sistema e põe o recuo à mão, contando o teclado.
- Sem AndroidX nem Compose: `Activity`, `WebView` e `HttpURLConnection` do próprio Android. A única dependência é o leitor de QR. O APK de release fica com uns 650 KB.

## Desenvolver

Precisa do JDK 17 e do Android SDK (35).

```sh
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                  # instala no aparelho ou emulador ligado
./gradlew lintDebug
```

No emulador, o host da máquina é `10.0.2.2`. Para o servidor aceitar esse Host, suba ele com `MINDBLOW_HOSTS=10.0.2.2` e conecte no app com `http://10.0.2.2:7341`.

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
