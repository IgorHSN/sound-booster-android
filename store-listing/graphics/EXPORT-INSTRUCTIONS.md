# Como exportar os assets para PNG

## Ícone 512×512

1. Abra `icon-512.html` no Chrome
2. Abra o DevTools (F12) → aba **Console**
3. Cole e execute:
```javascript
const canvas = document.querySelector('canvas');
const link = document.createElement('a');
link.download = 'loudify-icon-512.png';
link.href = canvas.toDataURL('image/png');
link.click();
```
4. O arquivo `loudify-icon-512.png` será baixado

## Feature Graphic 1024×500

1. Abra `feature-graphic.html` no Chrome
2. Abra o DevTools (F12) → aba **Console**
3. Cole e execute:
```javascript
const canvas = document.querySelector('canvas');
const link = document.createElement('a');
link.download = 'loudify-feature-graphic.png';
link.href = canvas.toDataURL('image/png');
link.click();
```
4. O arquivo `loudify-feature-graphic.png` será baixado

## Screenshots do app (obrigatório: mínimo 2)

1. Com o app aberto no Moto G84, pressione **Volume Baixo + Power** para tirar screenshot
2. Transfira via USB ou Google Fotos
3. Tamanho mínimo exigido pela Play Store: 320×568px
