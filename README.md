# Wildgrass

Demonstração 3D em primeira pessoa para Windows. O machado aparece em primeiro plano e golpeia com o botão esquerdo do mouse.

## Executar

Use um JDK 21 completo e um driver com suporte a OpenGL 3.3. Em PowerShell, na pasta do projeto, configure `JAVA_HOME` para o JDK 21 instalado e execute:

```powershell
$env:JAVA_HOME = 'C:\Users\Rafael\.jdks\ms-21.0.12.1'
.\gradlew.bat run
```

Se aparecer `could not open ...\lib\jvm.cfg`, o `JAVA_HOME` aponta para uma instalação incompleta. Use o caminho acima nesta instalação; a pasta temporária `javaengine-jdk21` não é um JDK completo.

O wrapper baixa o Gradle e as dependências na primeira execução. Escolha **Jogar** na tela inicial. Use WASD para andar, segure Shift para correr e segure Ctrl para agachar e andar mais devagar. **F5** alterna entre primeira pessoa, terceira pessoa por trás e terceira pessoa de frente. Mova o mouse para olhar e clique com o botão esquerdo para usar o item equipado. **Esc** pausa e retoma a partida. Aproxime-se da árvore e mire o ponto central no tronco para ouvir o impacto e ver as lascas de madeira.

**Configurações**, na tela inicial e no menu de pausa, permite ajustar o volume geral, a sensibilidade do mouse, o tremor da câmera, o balanço dos itens, a pixelização e o VSync. Use o mouse ou as setas do teclado. As alterações são salvas automaticamente em `%LOCALAPPDATA%\Wildgrass\settings.properties` no Windows.

Cinco machadadas derrubam a árvore. O impacto faz o tronco oscilar e o machado recuar. A árvore se separa do toco e permanece tombada. A queda levanta poeira, e a quebra, a batida no chão e a coleta têm sons próprios. Passe perto das três toras deixadas ao lado do tronco para recolhê-las automaticamente.

A barra inferior esquerda tem sete slots. Use **1–7** ou a roda do mouse para selecionar um deles. O machado começa no primeiro slot e o isqueiro no segundo. As toras ocupam pilhas de até 20 e completam uma pilha existente antes de ocupar um slot livre. Se a barra encher, as toras extras ficam no chão.

Com o isqueiro equipado, clique para abrir a tampa e acender. Aproxime-se e mantenha o ponto central sobre madeira por cerca de meio segundo. A árvore, o toco e as toras no chão podem queimar. Outro clique fecha a tampa. Toras em chamas voltam a poder ser recolhidas depois de apagar.

Use **3** para equipar o facão. Clique perto da relva alta para cortar com golpes diagonais que alternam o lado. A relva dobra com o vento e se abre quando você passa por ela.

Para incendiar a relva, equipe o isqueiro com **2**, acenda a chama e mire de perto por cerca de meio segundo. O fogo passa entre faixas de relva, avança mais rápido a favor do vento e pode pegar em árvores próximas. Abra faixas cortadas com o facão para interromper o avanço naquele trecho.

Bandos de pássaros sobrevoam a pradaria. O canto varia e fica mais suave com a distância.

Pressione **E** para abrir a mochila. Clique em um item e no slot de destino para mover, trocar ou juntar a pilha. No mesmo painel, use gravetos com pedras para fabricar flechas, ou gravetos com fibra seca para fabricar tochas.

O arco começa no **slot 4**, com 12 flechas. Segure o botão direito para mirar com zoom e o esquerdo para puxar a corda e ver a trajetória. O aviso sonoro e a barra dourada marcam a carga máxima. Solte o esquerdo para disparar e chegue perto da flecha caída para recuperá-la. O **slot 6** começa com pedras arremessáveis e o **slot 7** com uma tocha que pode acender materiais inflamáveis.

A mochila começa com **3 dinamites**. Pressione **E** para colocar a pilha em um dos sete slots, selecione-a e segure o botão direito para ver a trajetória em arco. Clique com o esquerdo para arremessar. O pavio acende durante o voo e a explosão derruba árvores próximas, corta a relva, solta partículas e deixa uma mancha no chão. O impacto balança a câmera e a ferramenta; a intensidade acompanha a opção **Tremor da câmera**.

Na mochila, fabrique **1 fogueira** com **3 gravetos, 2 pedras e 1 fibra seca**. Coloque-a na barra rápida e olhe para o chão: a prévia indica onde ela cabe. Clique para posicioná-la. Depois, equipe o isqueiro, abra a tampa e mantenha a mira sobre a fogueira de perto para acendê-la. Ela ilumina o entorno e permanece acesa durante esta partida.

As vacas procuram relva quando têm fome. Um trecho pastado cresce de novo em cerca de **32 segundos**. Elas fogem por alguns segundos depois de um ferimento e deixam três porções de carne crua ao morrer. Aproxime-se e pressione **F** para recolher uma porção. Com carne crua na mochila, mire numa fogueira acesa e pressione **F** para colocar uma porção no fogo. Quando a barra de cozimento terminar, pressione **F** de novo para recolher a carne cozida.

## Multiplayer LAN

Os dois computadores precisam estar na mesma rede local ou conectados à mesma sala no Radmin VPN. O anfitrião inicia o jogo, escolhe **Jogar solo**, abre o menu com **Esc** e seleciona **Abrir para LAN**. O jogo passa a escutar pacotes UDP na porta **7777**. O amigo seleciona **Conectar por IP** na tela inicial e digita o IPv4 do anfitrião mostrado no Radmin. Se o Windows pedir autorização de firewall, permita o aplicativo na rede do Radmin.

A sessão aceita **dois jogadores**. Os dois precisam usar a mesma versão do jogo. O personagem remoto acompanha o olhar, mostra o item equipado e anima o uso das ferramentas. A sessão transmite arremessos, disparos, explosões, golpes na vegetação e o início de incêndios. O anfitrião mantém o estado das vacas, da relva pastada, da carne e do cozimento. Quem entra depois recebe esse estado. A carne recolhida entra no inventário pessoal de quem a pegou. A coleta de toras e outros itens já existentes continua local. Se o anfitrião sair, o convidado retorna ao menu inicial.

## Modelo e renderização

Os dois sobreviventes da LAN estão no arquivo [wildgrass-survivors.blend](art/wildgrass-survivors.blend), com esqueleto e peças editáveis. O script [build_survivors.py](art/build_survivors.py) recria a cena no Blender e exporta os modelos usados pelo jogo para os arquivos `.wavatar`.

O arquivo [axe.obj](src/main/resources/models/axe.obj) contém a malha. Mantenha [axe.mtl](src/main/resources/models/axe.mtl) na mesma pasta ao importar o machado no Blender.

A janela inicial tem 960 × 540 pixels e a configuração Clássica renderiza a cena em 320 × 180. Ao redimensionar, a engine usa um fator inteiro para manter os pixels quadrados. As opções Fina e Forte mudam a resolução-alvo, e o OpenGL amplia o cenário com `GL_NEAREST`.

Para testar outra malha empunhada, exporte do Blender em OBJ com Y para cima, faces trianguladas e o MTL junto do OBJ. Use a escala e a posição do cabo de `axe.obj` como referência. A engine usa a geometria, as normais e as cores difusas (`Kd`) do MTL.

```powershell
.\gradlew.bat run '--args="C:\Modelos\meu modelo.obj"'
```
