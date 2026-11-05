using MotionWatch.Models;

namespace MotionWatch.Events;

public record GestoDetectadoMessage(Gesto Gesto, double Intensidad, DateTime Fecha);
public record LatenciaMedidaMessage(int Milisegundos);
public record ConexionCambiadaMessage(bool Conectado);