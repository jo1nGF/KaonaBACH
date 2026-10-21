# B.A.C.H. - Generative Composition for Android

> Port Android do módulo Eurorack **Kaona B.A.C.H.** (4-voice generative composition generator)
> Para fins de estudo e conhecimento, sem intuito comercial.

## 📖 Sobre

O B.A.C.H. é um instrumento de composição generativa em tempo real que cria estruturas melódicas, harmônicas e rítmicas usando 12 linguagens composicionais distintas, baseadas em técnicas históricas e algoritmos matemáticos.

Este app Android emula fielmente o módulo hardware, enviando sinais MIDI para outros aplicativos MIDI no mesmo dispositivo.

## 🎵 Linguagens Composicionais (12)

| Linguagem | Descrição |
|-----------|-----------|
| **Fugue** | Exposição clássica com sujeito, resposta, contra-sujeito e episódios de contraponto |
| **Canon** | Imitação canônica com transposição, retrogradação, inversão, aumentação |
| **Proba** | Cadeias de Markov com vocabulários de transição e aleatoriedade direcionada |
| **Tintinnabuli** | Técnica de Arvo Pärt - voz melódica + vozes triádicas |
| **Minimal** | Processos minimalistas: phase shifting, adição, substituição, hocketing |
| **Cellular** | Autômatos celulares 1D (254 regras) mapeados para música |
| **Tree** | L-systems (Lindenmayer) interpretados como caminhos melódicos |
| **Fractal** | Fibonacci, Thue-Morse, Sierpinski, mapa logístico |
| **Motif** | Cadeias de células melódicas/rítmicas de 32 coleções |
| **Hybrid** | Distribuição de múltiplos engines pelas 4 vozes |
| **Groove** | 64 templates rítmicos com papéis independentes |
| **Groove Mix** | Células rítmicas mapeadas em conteúdo melódico |

## 🎛️ 8 Controles Musicais

| Controle | Função |
|----------|--------|
| **Material** | Seleciona vocabulário base, semente, ou conjunto de regras |
| **Memory** | Persistência - quanto tempo o material gerado sobrevive |
| **Growth** | Complexidade estrutural e comprimento da forma |
| **Morph** | Transformações: rotação, inversão, retrogradação, transposição |
| **Density** | Número de eventos ativos e vozes tocando |
| **Tension** | Afastamento harmônico do estado estável |
| **Interaction** | Relações inter-vozes (diálogo entre as 4 vozes) |
| **Humanize** | Micro-variações orgânicas no timing e dinâmica |

## 🔊 Saídas (4 Vozes)

Cada uma das 4 vozes independentes envia via MIDI:
- **Note On/Off** (equivalente ao GATE + PITCH do hardware)
- **CC1 Modulation** (equivalente ao MOD output 0-5V)
- **Canal MIDI independente** por voz (configurável 1-16)

## 🎯 Break Effects (8 caracteres)

| Break | Efeito |
|-------|--------|
| Auto | Seleção automática baseada no Tension |
| Silence | Silêncio total |
| Agitation | Atividade rítmica rápida e densa |
| Suspension | Sustentação de notas longas |
| Fragment | Rajadas curtas fragmentadas |
| Pulse | Pulso rítmico regular |
| Erosion | Afinamento gradual |
| Expansion | Expansão de registro e densidade |

## 🔧 Como Compilar

### Pré-requisitos
- Android Studio Hedgehog (2023.1) ou superior
- JDK 17
- Android SDK 34
- Gradle 8.5

### Passos
1. Clone o repositório
2. Abra no Android Studio
3. Sync Gradle
4. Build > Make Project
5. Run no dispositivo Android (API 23+)

### Nota sobre MIDI
O app se registra como um **Virtual MIDI Device** no sistema Android. Outros apps MIDI (sintetizadores, DAWs) podem se conectar ao "B.A.C.H. Generative Composer" nas configurações MIDI para receber as notas geradas.

## 📱 Requisitos
- Android 6.0+ (API 23)
- Suporte a MIDI (android.software.midi)

## 📁 Estrutura do Projeto

```
app/src/main/java/com/kaona/bach/
├── BachApplication.kt          # Application class
├── MainActivity.kt             # Main activity with Compose
├── engine/                     # Core generative engines
│   ├── MusicTheory.kt          # Scales, chords, voice leading
│   ├── CompositionEngine.kt    # Base interface
│   ├── BachEngine.kt           # Main engine + params + state
│   ├── FugueEngine.kt          # Classical fugue
│   ├── CanonEngine.kt          # Canonical imitation
│   ├── ProbaEngine.kt          # Markov chains
│   ├── TintinnabuliEngine.kt   # Arvo Pärt technique
│   ├── MinimalEngine.kt        # Minimalist processes
│   ├── CellularEngine.kt      # Cellular automata
│   ├── TreeEngine.kt           # L-systems
│   ├── FractalEngine.kt        # Fractal/chaos math
│   ├── MotifEngine.kt          # Melodic cell chains
│   └── GrooveEngine.kt         # Rhythm templates + mix
├── midi/                       # MIDI output layer
│   ├── BachMidiService.kt      # Android Virtual MIDI Service
│   └── MidiOutputManager.kt    # Note scheduling & routing
├── model/                      # Data models
│   └── PresetManager.kt        # 128 presets save/load
├── viewmodel/                  # MVVM architecture
│   └── BachViewModel.kt        # UI state management
└── ui/                         # Jetpack Compose UI
    ├── BachMainScreen.kt       # Main screen layout
    ├── theme/
    │   └── BachTheme.kt        # Dark theme matching hardware
    └── components/
        ├── ParameterKnob.kt    # Rotary knob component
        ├── BachDisplay.kt      # Circular OLED-style display
        ├── TransportControls.kt # Play/Stop/Reset/Break + BPM
        ├── HeaderAndPanels.kt  # Header, CV buttons, outputs, language selector
        └── SettingsAndPresets.kt # Settings & presets panels
```

## ⚖️ Disclaimer

Este projeto é uma recriação para fins educacionais e de estudo do módulo Kaona B.A.C.H.
Não possui intuito comercial. Todos os direitos do design original pertencem à Kaona.
O B.A.C.H. original é um produto comercial disponível em: https://www.kaona.fr/

## 🎶 Créditos

- Design original: **Kaona** (https://www.kaona.fr)
- Conceito: Módulo Eurorack B.A.C.H. (4-voice generative composition generator)
- Port Android: Projeto educacional
