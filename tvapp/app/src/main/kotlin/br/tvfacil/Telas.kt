package br.tvfacil

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import br.tvfacil.core.Camada
import br.tvfacil.core.Busca
import br.tvfacil.core.Canal
import br.tvfacil.core.Catalogo
import br.tvfacil.core.EstadoVideo
import br.tvfacil.core.Fase
import br.tvfacil.core.Grupo
import kotlinx.coroutines.delay

// Cores e medidas do protótipo aprovado (docs/plano-tvapp.md). A tela do stick tem 853x480 dp (= protótipo / 1,5).
val FUNDO = Color(0xFF0E1116)
val TEXTO = Color(0xFFF4F1EA)
private val FOCO = Color(0xFFFFC94D)
private val SOBRE_FOCO = Color(0xFF1A1300)
private val CINZA = Color(0xFFC3C9D1)
private val PAINEL = Color(0xF20E1116)
private val CARTAO = Color(0xFF1A1F27)
private val BORDA = Color(0xFF3A424E)
private val LATERAL = Color(0xFF141920)
private val SELO = Color(0xFF2A313C)
val ATKINSON = FontFamily(Font(R.font.atkinson_regular), Font(R.font.atkinson_bold, FontWeight.Bold))

private const val BARRA_MS = 6_000L
private val LARGURA_LISTA = 387.dp

interface Acoes {
    fun escolher(canal: Canal, grupo: String, fecharLista: Boolean = true)
    fun tempoEsgotado(canal: Canal)
    fun zapear(passo: Int)
    fun tentarDeNovo()
    fun mostrar(camada: Camada)
    fun alternarFavorito()
    fun voltarAoAnterior()
    fun mudarTermo(texto: String)
}

@Composable
fun Tela(
    canais: List<Canal>?,
    grupos: List<Grupo>,
    grupoAtual: String,
    favoritos: Set<String>,
    video: EstadoVideo,
    camada: Camada,
    anterior: Canal?,
    faixa: String?,
    esperaMaximaMs: Long,
    termo: String,
    player: VlcPlayer,
    acoes: Acoes,
) {
    if (canais == null) {
        Abertura()
        return
    }
    LaunchedEffect(video) {
        val canal = video.canal
        if (video.fase == Fase.ABRINDO && canal != null) {
            delay(esperaMaximaMs)
            acoes.tempoEsgotado(canal)
        }
    }
    val canal = video.canal
    var barra by remember { mutableStateOf(true) }
    LaunchedEffect(canal, camada) {
        barra = true
        delay(BARRA_MS)
        barra = false
    }
    // Com o mouse: clicar no video abre a lista (no controle, e o OK).
    Box(
        Modifier.fillMaxSize().background(FUNDO).pointerInput(camada) {
            detectTapGestures { if (camada == Camada.ASSISTINDO) acoes.mostrar(Camada.LISTA) }
        },
    ) {
        AndroidView(factory = { ctx -> player.criarTela(ctx) }, modifier = Modifier.fillMaxSize())
        // Com a lista aberta, os avisos vao para a area livre a direita dela.
        val livre = if (camada == Camada.LISTA || camada == Camada.BUSCA) LARGURA_LISTA else 0.dp
        when {
            canais.isEmpty() -> Aviso("Lista de canais não encontrada.\n\n/sdcard/iptv/canais_ok.m3u", livre)
            video.fase == Fase.PARADO -> Aviso("Aperte OK para escolher um canal", livre)
            video.fase == Fase.ABRINDO -> Aviso("Abrindo ${canal?.nome}…", livre)
            video.fase == Fase.INDISPONIVEL && canal != null -> Indisponivel(canal, camada == Camada.ASSISTINDO, livre, acoes)
        }
        when (camada) {
            Camada.ASSISTINDO -> if (barra && canal != null && video.fase != Fase.INDISPONIVEL) {
                val grupo = grupos.firstOrNull { it.nome == grupoAtual }
                val posicao = grupo?.canais?.indexOf(canal) ?: -1
                val onde = if (grupo != null && posicao >= 0) "${grupo.nome} · canal ${posicao + 1} de ${grupo.canais.size}" else canal.grupo
                BarraCanal(canal, canal.url in favoritos, onde, Modifier.align(Alignment.BottomCenter))
            }
            Camada.LISTA -> ListaCanais(grupos, grupoAtual, canal, acoes)
            Camada.BUSCA -> BuscaCanais(canais, termo, canal, acoes)
            Camada.GRADE -> GradeCanais(grupos, grupoAtual, canal, acoes)
            Camada.CONFIGURAR -> Configurar()
            Camada.MENU -> Menu(canal, canal?.url in favoritos, anterior, acoes, Modifier.align(Alignment.CenterEnd))
        }
        if (faixa != null) {
            Text(
                faixa,
                color = TEXTO,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopCenter).background(Color(0xCC000000)).padding(12.dp),
            )
        }
    }
}

@Composable
private fun BarraCanal(canal: Canal, favorito: Boolean, onde: String, modifier: Modifier) {
    Row(
        modifier.padding(start = 32.dp, end = 32.dp, bottom = 27.dp).fillMaxWidth()
            .background(PAINEL, RoundedCornerShape(13.dp)).padding(horizontal = 21.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(19.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(canal.nome, color = TEXTO, fontSize = 29.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (favorito) Text("★", color = FOCO, fontSize = 24.sp)
            }
            Text(onde, color = CINZA, fontSize = 16.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Dica("CH ▲▼", "trocar canal")
            Dica("OK", "ver lista")
            Dica("≡", "menu")
        }
    }
}

@Composable
private fun Dica(tecla: String, texto: String, tamanho: TextUnit = 13.sp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            tecla,
            color = TEXTO,
            fontSize = tamanho,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.background(BORDA, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Text(texto, color = CINZA, fontSize = tamanho, maxLines = 1)
    }
}

@Composable
private fun ListaCanais(grupos: List<Grupo>, grupoAtual: String, atual: Canal?, acoes: Acoes) {
    if (grupos.isEmpty()) return
    var indice by remember { mutableIntStateOf(grupos.indexOfFirst { it.nome == grupoAtual }.coerceAtLeast(0)) }
    fun vizinho(passo: Int) = Math.floorMod(indice + passo, grupos.size)
    val grupo = grupos[indice.coerceIn(grupos.indices)]
    val alvo = grupo.canais.indexOf(atual).coerceAtLeast(0)
    val rolagem = rememberLazyListState()
    val foco = remember { FocusRequester() }
    LaunchedEffect(grupo.nome) {
        rolagem.scrollToItem(alvo)
        delay(50) // espera o item aparecer na tela para poder receber o foco
        runCatching { foco.requestFocus() }
    }
    Column(
        Modifier.fillMaxHeight().width(LARGURA_LISTA).background(PAINEL).padding(horizontal = 21.dp, vertical = 24.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { indice = vizinho(-1); true }
                    Key.DirectionRight -> { indice = vizinho(+1); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "◀ ${grupos[vizinho(-1)].nome}", color = CINZA, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).pointerInput(grupos) { detectTapGestures { indice = vizinho(-1) } },
            )
            Text(grupo.nome, color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                "${grupos[vizinho(+1)].nome} ▶", color = CINZA, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).pointerInput(grupos) { detectTapGestures { indice = vizinho(+1) } },
            )
        }
        LazyColumn(state = rolagem, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(grupo.canais) { i, c ->
                // 1º OK so troca o video (da para ir olhando); OK no canal que ja esta no ar fecha a lista.
                ItemCanal(c, noAr = c == atual, modifier = if (i == alvo) Modifier.focusRequester(foco) else Modifier) {
                    if (c == atual) acoes.mostrar(Camada.ASSISTINDO) else acoes.escolher(c, grupo.nome, fecharLista = false)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Dica("▲▼", "escolher", 12.sp)
            Dica("◀ ▶", "grupo", 12.sp)
            Dica("OK", "assistir", 12.sp)
            Dica("OK de novo", "fechar", 12.sp)
            Dica("Voltar", "sair", 12.sp)
        }
    }
}

@Composable
private fun BuscaCanais(canais: List<Canal>, termo: String, atual: Canal?, acoes: Acoes) {
    val resultados = remember(canais, termo) { Busca.filtrar(canais, termo) }
    val campo = remember { FocusRequester() }
    var campoFocado by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { campo.requestFocus() }
    }
    Column(
        Modifier.fillMaxHeight().width(LARGURA_LISTA).background(PAINEL).padding(horizontal = 21.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text("Buscar canal", color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = termo,
            onValueChange = acoes::mudarTermo,
            singleLine = true,
            textStyle = TextStyle(color = TEXTO, fontSize = 22.sp, fontFamily = ATKINSON),
            cursorBrush = SolidColor(FOCO),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().focusRequester(campo).onFocusChanged { campoFocado = it.isFocused }
                .border(3.dp, if (campoFocado) FOCO else BORDA, RoundedCornerShape(9.dp)).padding(12.dp),
            decorationBox = { texto ->
                if (termo.isEmpty()) Text("nome do canal", color = CINZA, fontSize = 22.sp)
                texto()
            },
        )
        when {
            termo.isBlank() -> Text("Aperte OK para digitar o nome do canal.", color = CINZA, fontSize = 16.sp)
            resultados.isEmpty() -> Text("Nenhum canal com “$termo”", color = CINZA, fontSize = 16.sp)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(resultados) { c ->
                // Mesma regra da lista: 1º OK troca o video, OK no canal que ja esta no ar fecha.
                ItemCanal(c, noAr = c == atual, Modifier, detalhe = Catalogo.grupoDe(c)) {
                    if (c == atual) acoes.mostrar(Camada.ASSISTINDO) else acoes.escolher(c, Catalogo.grupoDe(c), fecharLista = false)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Dica("OK", "digitar", 12.sp)
            Dica("▼", "resultados", 12.sp)
            Dica("OK de novo", "fechar", 12.sp)
        }
    }
}

@Composable
private fun ItemCanal(canal: Canal, noAr: Boolean, modifier: Modifier, detalhe: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = coresFoco(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(canal.nome, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detalhe != null) Text(detalhe, fontSize = 13.sp, maxLines = 1)
            }
            if (noAr) NoAr()
        }
    }
}

@Composable
private fun NoAr(modifier: Modifier = Modifier) {
    Text(
        "NO AR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.background(Color(0xFFE5484D), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

// Tela cheia (cobre o video; o som continua): grupos a esquerda, cartoes a direita. OK no cartao assiste e fecha.
@Composable
private fun GradeCanais(grupos: List<Grupo>, grupoAtual: String, atual: Canal?, acoes: Acoes) {
    if (grupos.isEmpty()) return
    var indice by remember { mutableIntStateOf(grupos.indexOfFirst { it.nome == grupoAtual }.coerceAtLeast(0)) }
    val grupo = grupos[indice.coerceIn(grupos.indices)]
    val alvo = grupo.canais.indexOf(atual).coerceAtLeast(0)
    val rolagem = rememberLazyGridState()
    val foco = remember { FocusRequester() }
    val grupoFoco = remember { FocusRequester() }
    var abriu by remember { mutableStateOf(false) }
    LaunchedEffect(grupo.nome) {
        rolagem.scrollToItem(alvo)
        if (abriu) return@LaunchedEffect
        abriu = true
        delay(50) // espera o cartao aparecer na tela para poder receber o foco
        runCatching { foco.requestFocus() }
    }
    Row(Modifier.fillMaxSize().background(FUNDO)) {
        // Padrao de TV: o grupo que recebe o foco (▲▼ nesta coluna) e o que aparece na grade.
        Column(
            Modifier.fillMaxHeight().width(200.dp).background(LATERAL).padding(horizontal = 13.dp, vertical = 24.dp)
                .focusRestorer(grupoFoco), // vindo da grade com ◀, cai no grupo atual
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Todos os canais", color = TEXTO, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 11.dp, bottom = 9.dp))
            grupos.forEachIndexed { i, g ->
                val escolhido = i == indice
                Surface(
                    onClick = { indice = i },
                    modifier = Modifier.fillMaxWidth().then(if (escolhido) Modifier.focusRequester(grupoFoco) else Modifier)
                        .onFocusChanged { if (it.isFocused) indice = i },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (escolhido) SELO else Color.Transparent,
                        contentColor = if (escolhido) TEXTO else CINZA,
                        focusedContainerColor = FOCO,
                        focusedContentColor = SOBRE_FOCO,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.nome, fontSize = 16.sp, fontWeight = if (escolhido) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${g.canais.size}", fontSize = 12.sp)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 27.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text(grupo.nome, color = TEXTO, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            LazyVerticalGrid(
                GridCells.Fixed(4),
                state = rolagem,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                items(grupo.canais.size) { i ->
                    val c = grupo.canais[i]
                    CartaoCanal(c, noAr = c == atual, if (i == alvo) Modifier.focusRequester(foco) else Modifier) { acoes.escolher(c, grupo.nome) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dica("◀ ▶ ▲ ▼", "escolher", 12.sp)
                Dica("OK", "assistir", 12.sp)
                Dica("Voltar", "fechar", 12.sp)
            }
        }
    }
}

@Composable
private fun CartaoCanal(canal: Canal, noAr: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focado by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(100.dp).onFocusChanged { focado = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = CARTAO, contentColor = TEXTO, focusedContainerColor = SELO, focusedContentColor = TEXTO),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, FOCO))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f), // sem aumentar: o stick e fraco
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(43.dp).background(if (focado) FOCO else SELO, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text(Catalogo.sigla(canal.nome), color = if (focado) SOBRE_FOCO else TEXTO, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(canal.nome, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (noAr) NoAr(Modifier.align(Alignment.TopEnd).padding(5.dp))
        }
    }
}

@Composable
private fun Menu(canal: Canal?, favorito: Boolean, anterior: Canal?, acoes: Acoes, modifier: Modifier) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { foco.requestFocus() }
    }
    Column(
        modifier.fillMaxHeight().width(320.dp).background(PAINEL).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text("Menu", color = TEXTO, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (canal != null) Text(canal.nome, color = CINZA, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Botao("Buscar canal", Modifier.focusRequester(foco)) { acoes.mostrar(Camada.BUSCA) }
        Botao("Todos os canais") { acoes.mostrar(Camada.GRADE) }
        if (canal != null) {
            Botao(if (favorito) "Tirar dos favoritos" else "★ Favoritar") { acoes.alternarFavorito() }
        }
        if (anterior != null) {
            Botao("Canal anterior:\n${anterior.nome}") { acoes.voltarAoAnterior() }
        }
        Botao("Configurar pelo celular") { acoes.mostrar(Camada.CONFIGURAR) }
        Spacer(Modifier.weight(1f))
        Dica("Voltar", "fechar o menu")
    }
}

// Enquanto esta tela esta aberta, a TV aceita a configuracao pelo navegador do celular (PIN). Fechou, fechou a porta.
@Composable
private fun Configurar() {
    val context = LocalContext.current
    val servidor = remember { ServidorConfig(context) }
    DisposableEffect(Unit) {
        servidor.iniciar()
        onDispose { servidor.parar() }
    }
    Column(
        Modifier.fillMaxSize().background(FUNDO).padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Configurar pelo celular", color = TEXTO, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        val ip = servidor.ip
        if (ip == null) {
            Text("A TV não está conectada a uma rede Wi-Fi.", color = TEXTO, fontSize = 22.sp)
        } else {
            Text("1. Conecte o celular no mesmo Wi-Fi da TV.", color = CINZA, fontSize = 20.sp)
            Text("2. No navegador do celular, abra:", color = CINZA, fontSize = 20.sp)
            Text("http://$ip:${ServidorConfig.PORTA}", color = FOCO, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("3. Digite o PIN:", color = CINZA, fontSize = 20.sp)
            Text(servidor.pin, color = FOCO, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Text(servidor.status, color = TEXTO, fontSize = 20.sp)
        }
        Spacer(Modifier.weight(1f))
        Dica("Voltar", "fechar (a configuração para de funcionar)")
    }
}

@Composable
private fun Indisponivel(canal: Canal, ativo: Boolean, livre: Dp, acoes: Acoes) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(canal, ativo) {
        if (!ativo) return@LaunchedEffect
        delay(50)
        runCatching { foco.requestFocus() }
    }
    Box(Modifier.fillMaxSize().background(FUNDO).padding(start = livre).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 507.dp).background(CARTAO, RoundedCornerShape(16.dp)).padding(horizontal = 37.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text("Este canal não está passando agora", color = TEXTO, fontSize = 29.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("${canal.nome} · ${canal.grupo}", color = CINZA, fontSize = 17.sp, textAlign = TextAlign.Center)
            // Sem os botoes quando a lista ou o menu estao abertos: o foco nao "escapa" para eles.
            if (ativo) {
                Spacer(Modifier.height(8.dp))
                Botao("Próximo canal ▶", Modifier.focusRequester(foco)) { acoes.zapear(+1) }
                Botao("Tentar de novo") { acoes.tentarDeNovo() }
                Botao("Ver lista de canais") { acoes.mostrar(Camada.LISTA) }
            }
        }
    }
}

@Composable
private fun Botao(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = coresFoco(),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(3.dp, BORDA)),
            focusedBorder = Border(BorderStroke(3.dp, FOCO)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(texto, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(11.dp))
    }
}

@Composable
private fun coresFoco() = ClickableSurfaceDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = TEXTO,
    focusedContainerColor = FOCO,
    focusedContentColor = SOBRE_FOCO,
    pressedContainerColor = FOCO,
    pressedContentColor = SOBRE_FOCO,
)

@Composable
fun Abertura() {
    Column(
        Modifier.fillMaxSize().background(FUNDO),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.banner), contentDescription = null, Modifier.size(320.dp, 180.dp))
        Text("TV Fácil", color = TEXTO, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text("Carregando canais…", color = TEXTO, fontSize = 24.sp)
    }
}

@Composable
private fun Aviso(texto: String, livre: Dp = 0.dp) {
    Box(Modifier.fillMaxSize().background(FUNDO).padding(start = livre).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(texto, color = TEXTO, fontSize = 28.sp, textAlign = TextAlign.Center)
    }
}
