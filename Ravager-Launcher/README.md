# Ravager Launcher

Launcher de código aberto para TV Box, Android TV e Google TV: simples, leve e sem anúncios.
*Open source launcher for TV boxes, Android TV and Google TV: simple, light and ad-free. English below.*

## O que ele faz

- Tela inicial com hora, data e clima, e apps em cards organizados em bandejas
- Dois layouts: Relógio em destaque e Compacto
- Temas: Cor do app, Apple TV, ATV Launcher, Mi TV Box, Android TV / Google TV e OmarchyTV
- Wallpapers: dunas em oito cores, ou seus próprios arquivos de imagem, GIF e vídeo MP4, da memória do aparelho ou de um pen drive, com miniaturas na lista
- Protetor de tela com modelos e prévia ao vivo: Relógio, Logo DVD e Chuva de letras
- Barra superior configurável: escolha os botões, a ordem, o texto, o relógio, a data e o clima
- Apps: tamanho dos cards, nome grande, nomes das categorias, renomear, trocar de categoria e mover com as setas. Os cards seguem o tema e mostram o banner do app; sem banner, mostram o ícone e o nome
- Menu de energia: suspender, reiniciar e desligar, pelo Shizuku ou com o comando ADB
- Captura opcional do botão Home, para sistemas que insistem no próprio launcher
- Fontes à escolha: Poppins, Barlow, Prompt e Oxygen
- Menus laterais, com os ajustes organizados por categoria
- Status do aparelho em tempo real, notificações, rede e USB
- Português e inglês
- Sem anúncios, sem recomendações e sem coleta de dados de uso

Requer Android 7.0 ou superior.

## Definir como launcher padrão

Em **Ajustes › Launcher e sistema › Definir como launcher padrão**, o launcher tenta abrir a tela do sistema. Se o aparelho não permitir, ele oferece duas alternativas, com o comando exato para o seu aparelho:

- **Shizuku:** instale e inicie o [Shizuku](https://shizuku.rikka.app), autorize o Ravager e toque em Aplicar.
- **ADB**, a partir de um computador ou celular:

  ```
  adb shell cmd package set-home-activity com.ravager.launcher/.home.HomeActivity
  ```

Para desfazer, repita o comando com o nome do launcher anterior, que a tela de ajuda mostra.

**Google TV:** a troca não é garantida. O launcher sempre confere o resultado e, se não funcionar, continua podendo ser aberto manualmente. Desativar o launcher original é possível, mas outro serviço do Google pode reativá-lo e o botão do YouTube no controle pode parar de funcionar.

## Notificações

O caminho é o mesmo: tela do sistema, Shizuku ou comando no ADB.

```
adb shell cmd notification allow_listener com.ravager.launcher/com.ravager.launcher.notifications.RavagerNotificationListener
```

Em aparelhos de pouca memória, o Android bloqueia esse acesso, mesmo com o comando. Nesses aparelhos, as notificações só aparecem com o Shizuku ativo; Abrir leva à tela da notificação ou inicia o app dela, e Remover tira a notificação só da lista do launcher.

## Limites conhecidos

- Se o botão Home abrir outro launcher mesmo com o Ravager como padrão, verifique se esse launcher tem um serviço de acessibilidade ligado. A tela **Definir como launcher padrão** avisa e ajuda a desligar.
- Vídeos e GIFs grandes podem pesar em aparelhos fracos. Eles só tocam com a tela inicial aberta.
- Sem root, o Shizuku precisa ser iniciado de novo depois de reiniciar o aparelho. Isso afeta o nome do Wi-Fi e as notificações em aparelhos de pouca memória. A troca de launcher continuou valendo no aparelho de teste.
- Sem Shizuku, o botão de rede mostra só "Wi-Fi", não o nome da rede.
- Leituras que o aparelho não libera, como temperatura, ficam ocultas.

## Privacidade

Não há anúncios nem coleta de dados de uso. Para mostrar o clima, o [GeoJS](https://www.geojs.io/) e a [Open-Meteo](https://open-meteo.com/) recebem o IP do aparelho. Para procurar atualizações, o GitHub também recebe. Os dois recursos podem ser desligados nos ajustes.

## Como compilar

Requer JDK 17 e o Android SDK.

```
./gradlew assembleDebug
```

Para a versão assinada, crie o arquivo `.ravager/keystore.properties` na sua pasta de usuário, fora do projeto, com `storeFile`, `storePassword`, `keyAlias` e `keyPassword`, e rode `./gradlew assembleRelease`.

## Licença

Criado por [DarkModeBR](https://github.com/DarkModeBR). Licença [MIT](LICENSE). Dados de clima da [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).

- **Wallpapers:** as oito versões de dunas foram geradas por código para este projeto e seguem a licença MIT.
- **Fontes:** Poppins, Barlow, Prompt e Oxygen, sob a SIL Open Font License 1.1. As licenças completas ficam em `app/src/main/assets/licenses/`.
- **Marcas:** os nomes de marcas nos temas e o protetor Logo DVD só indicam o estilo que eles lembram. As marcas pertencem aos seus donos, e este projeto não tem relação com eles.

---

## English

A launcher home screen with clock, date and weather, apps as cards in trays, two layouts, themes including black and white, live device status, notifications, network and USB state, and a screensaver. No ads, no recommendations, no usage data collection. Requires Android 7.0 or later.

**Set as default:** open **Settings › Launcher and system › Set as default launcher**. If the device does not allow it, the screen offers Shizuku or this ADB command, with the exact undo command for your device:

```
adb shell cmd package set-home-activity com.ravager.launcher/.home.HomeActivity
```

On Google TV the switch is not guaranteed. The launcher always checks the result and stays usable when opened manually.

**Notifications:** same path. On low-memory devices Android blocks this access even with the command, so notifications only appear while Shizuku is running.

**Privacy:** GeoJS and Open-Meteo receive the device IP address to show the weather, and GitHub receives it to check for updates. Both features can be turned off in the settings.

**License:** created by [DarkModeBR](https://github.com/DarkModeBR), under the [MIT](LICENSE) license. Weather data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).
