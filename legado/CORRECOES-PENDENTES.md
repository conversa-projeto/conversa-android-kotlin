# Correções Pendentes - Aguardando Commit

## Lista de Correções Aplicadas (NÃO COMMITADAS)

### 1. **Type mismatch em ChamadaScreen.kt**
- **Arquivo**: `ChamadaService.kt`
- **Problema**: `getParticipantes()` retornava `List<ParticipanteItem>` mas UI esperava `List<ParticipanteUI>`
- **Solução**: Adicionado mapeamento de `ParticipanteItem` para `ParticipanteUI` no método
- **Status**: ✅ CORRIGIDO

### 2. **Unresolved reference de ícones Material em CallControls.kt e OutgoingCallScreen.kt**
- **Arquivos**: `CallControls.kt`, `OutgoingCallScreen.kt`
- **Problema**: Ícones como `CallEnd`, `MicOff`, `PersonAdd`, `VolumeUp`, `VolumeDown`, `VolumeOff` não existem no `Icons.Filled` padrão
- **Solução**: Substituídos por ícones básicos disponíveis:
  - `CallEnd` → `Close`
  - Mute: `Close/Phone` (mutado/não mutado)
  - Speaker: `Settings/Phone` (ativo/inativo)
  - `PersonAdd` → `Add`
- **Nota**: Para ícones corretos, seria necessário adicionar dependency `material-icons-extended` ou usar drawable resources
- **Status**: ✅ CORRIGIDO

## Resumo das Correções Anteriores (JÁ COMMITADAS)

1. **`74440af`** - Ambiguidade de sobrecarga em `finalizarChamada()`
2. **`4e5c992`** - Adiciona CHAMADA_RECEBIDA ao enum TipoEventoChamadaUI
3. **`dad70ec`** - Corrige referência de ícones (ic_speaker → ic_volume_up)
4. **`c3db87b`** - Corrige referência ao enum em ChamadaActivity
5. **`27de6ac`** - Corrige todas as referências em ChamadaScreen

## Próximos Passos

Aguardando mais correções do usuário antes de fazer commit em lote.

---
**Última atualização**: Correção do type mismatch ParticipanteItem/ParticipanteUI
