package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.validations.PedidoValidacao;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ClienteService clienteService;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = (List<Usuario>) usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        log.info("Carregando fragmento: usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = usuarioService.buscarPorId(id).map(u -> {
            u.setPassword(null);
            model.addAttribute("usuario", u);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }

        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.salvarUsuario(usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.deletarUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    // ========================
    // AUDITORIA
    // ========================
    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAttribute("paginaAuditoria", auditoriaService.listarRegistros(pagina));
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }

    // ========================
    // PEDIDOS
    // ========================
    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        Map<String, String> nomesClientes = clienteRepository.findAll().stream()
                .collect(Collectors.toMap(Cliente::getId, Cliente::getNome, (a, b) -> a));
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("nomesClientes", nomesClientes);
        model.addAttribute("content", "pedidos :: content");
        log.info("Carregando fragmento: pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/{id}")
    public String detalhePedido(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return pedidoService.buscarPorId(id).map(p -> {
            Map<String, String> nomesClientes = clienteRepository.findAll().stream()
                    .collect(Collectors.toMap(Cliente::getId, Cliente::getNome, (a, b) -> a));
            double subtotalItens = p.getItens() == null ? 0.0
                    : p.getItens().stream().mapToDouble(i -> i.getSubtotal() != null ? i.getSubtotal() : 0.0).sum();
            model.addAttribute("pedido", p);
            model.addAttribute("subtotalItens", subtotalItens);
            model.addAttribute("nomesClientes", nomesClientes);
            model.addAttribute("proximosStatus", PedidoValidacao.proximosStatus(p.getStatus()));
            boolean podeDesconto = !StatusPedido.CANCELADO.equals(p.getStatus())
                    && !PedidoValidacao.naoPodeDesconto(p.getStatus());
            model.addAttribute("podeAplicarDesconto", podeDesconto);
            model.addAttribute("podeCancelar", !PedidoValidacao.naoPodeCancelar(p.getStatus()));
            model.addAttribute("content", "pedidos/detalhe :: content");
            return "layout";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("erro", "Pedido não encontrado.");
            return "redirect:/pedidos";
        });
    }

    @GetMapping("/pedidos/novo")
    public String novoPedidoForm(Model model) {
        model.addAttribute("pedido", new Pedido());
        model.addAttribute("clientes", clienteService.listarClientes());
        List<Produto> produtosAtivos = produtoService.listarProdutos().stream()
                .filter(p -> p.getAtivo() == null || Boolean.TRUE.equals(p.getAtivo()))
                .toList();
        model.addAttribute("produtos", produtosAtivos);
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }

    @PostMapping("/pedidos/{id}/status")
    public String atualizarStatusPedido(@PathVariable String id,
            @RequestParam String status,
            RedirectAttributes redirectAttributes) {
        try {
            pedidoService.atualizarStatus(id, status);
            redirectAttributes.addFlashAttribute("sucesso", "Status do pedido atualizado.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelarPedidoWeb(@PathVariable String id,
            @RequestParam String justificativa,
            RedirectAttributes redirectAttributes) {
        try {
            pedidoService.cancelarPedido(id, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido cancelado.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDescontoPedido(@PathVariable String id,
            @RequestParam
            Double descontoPercentual,
            RedirectAttributes redirectAttributes) {
        try {
            pedidoService.aplicarDesconto(id, descontoPercentual);
            redirectAttributes.addFlashAttribute("sucesso", "Desconto aplicado com sucesso!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/salvar")
    public String salvarPedido(@ModelAttribute Pedido pedido, RedirectAttributes redirectAttributes) {
        if (pedido.getItens() != null) {
            pedido.setItens(pedido.getItens().stream()
                    .filter(i -> i.getProdutoId() != null && !i.getProdutoId().isBlank())
                    .toList());
        }
        try {
            pedidoService.criarPedido(pedido);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
            return "redirect:/pedidos";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos/novo";
        }
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        Produto novoProduto = new Produto();
        novoProduto.setAtivo(true);
        model.addAttribute("produto", novoProduto);
        model.addAttribute("content", "produtos/lista :: content");
        log.info("Carregando fragmento: produtos/lista :: content");
        return "layout";
    }

    @PostMapping("/produtos/salvar")
    public String salvarProduto(@ModelAttribute Produto produto, RedirectAttributes redirectAttributes) {
        if (produto.getAtivo() == null) {
            produto.setAtivo(false);
        }
        produtoService.salvar(produto);
        redirectAttributes.addFlashAttribute("sucesso", "Produto salvo com sucesso!");
        return "redirect:/produtos";
    }

    @GetMapping("/produtos/editar/{id}")
    public String editarProdutoForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        return produtoService.buscarPorId(id).map(p -> {
            model.addAttribute("produto", p);
            model.addAttribute("content", "produtos/form :: content");
            return "layout";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("erro", "Produto não encontrado.");
            return "redirect:/produtos";
        });
    }

    @PostMapping("/produtos/editar/{id}")
    public String atualizarProduto(@PathVariable String id, @ModelAttribute Produto produto,
            RedirectAttributes redirectAttributes) {
        produto.setId(id);
        if (produto.getAtivo() == null) {
            produto.setAtivo(false);
        }
        try {
            produtoService.salvar(produto);
            redirectAttributes.addFlashAttribute("sucesso", "Produto atualizado com sucesso!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/produtos/editar/" + id;
        }
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/excluir/{id}")
    public String excluirProduto(@PathVariable String id, RedirectAttributes redirectAttributes) {
        produtoService.excluir(id);
        redirectAttributes.addFlashAttribute("sucesso", "Produto excluído com sucesso!");
        return "redirect:/produtos";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        List<Cliente> clientes = clienteService.listarClientes();
        model.addAttribute("clientes", clientes);
        model.addAttribute("content", "clientes/lista :: content");
        log.info("Carregando fragmento: clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        clienteService.salvar(cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = clienteService.buscarPorId(id).map(c -> {
            model.addAttribute("cliente", c);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }

        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id,
            @ModelAttribute Cliente cliente,
            RedirectAttributes redirectAttributes) {
        cliente.setId(id);
        clienteService.salvar(cliente);
        redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    // ========================
    // OUTRAS ROTAS
    // ========================
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado(Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("content", "acesso-negado :: content");
        return "layout";
    }

    @GetMapping("/teste")
    public String teste() {
        return "teste";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
