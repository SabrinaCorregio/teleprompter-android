# Teleprompter flutuante (Android)

App Android com janela flutuante que fica por cima da câmera enquanto você grava.

## Como gerar o APK (sem Android Studio)

1. Crie um repositório no GitHub (pode ser privado).
2. Envie TODO o conteúdo desta pasta para o repositório (inclusive a pasta `.github`).
3. Abra a aba **Actions**. O build roda sozinho a cada envio (ou use "Run workflow").
4. Quando terminar (uns 3 a 6 minutos), abra a execução e baixe o artefato **teleprompter-apk**.
5. Descompacte, envie o `app-debug.apk` para o celular e instale (permita "instalar apps desconhecidos").

## Primeiro uso

1. Abra o app, crie ou importe um roteiro.
2. Toque em **Abrir teleprompter flutuante**. Na primeira vez o Android pede a permissão
   "Exibir sobre outros apps": ative e volte.
3. Abra a câmera do celular e comece a gravar.
4. Toque no texto para iniciar (contagem 3-2-1) ou pausar.

## Controles da janela

- `≡ 30` : arraste por aqui para mover. O número é a velocidade.
- `▶ / ❚❚` : iniciar/pausar. `−` `+` : velocidade. `A−` `A+` : tamanho da letra.
- `⇋` : espelhar texto. `◐` : transparência do fundo. `✕` : fechar.
- `◢` (canto inferior direito): arraste para redimensionar.
- Arraste o texto com o dedo para voltar ou avançar.
